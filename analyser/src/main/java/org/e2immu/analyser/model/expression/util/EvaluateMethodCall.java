/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model.expression.util;

import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.e2immu.analyser.analyser.Property.NOT_NULL_EXPRESSION;

public class EvaluateMethodCall {
    private static final Logger LOGGER = LoggerFactory.getLogger(EvaluateMethodCall.class);

    private final EvaluationResult context;
    private final AnalyserContext analyserContext;
    private final Primitives primitives;
    private final Identifier identifier;
    private final MethodCall methodCall;
    private final MethodInfo methodInfo;

    public EvaluateMethodCall(EvaluationResult evaluationContext, MethodCall methodCall) {
        this.context = Objects.requireNonNull(evaluationContext);
        this.primitives = evaluationContext.getPrimitives();
        this.analyserContext = evaluationContext.getAnalyserContext();
        this.identifier = Objects.requireNonNull(methodCall.identifier);
        this.methodCall = methodCall;
        this.methodInfo = methodCall.methodInfo;
    }

    public EvaluationResult methodValue(DV modified,
                                        MethodAnalysis methodAnalysis,
                                        boolean objectIsImplicit,
                                        Expression objectValue,
                                        ParameterizedType concreteReturnType,
                                        List<Expression> parameters,
                                        ForwardEvaluationInfo forwardEvaluationInfo,
                                        Expression modifiedInstance) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
        boolean recursiveCall = MethodCall.recursiveCall(methodInfo, context.evaluationContext());
        if (recursiveCall) {
            MethodCall methodValue = new MethodCall(identifier, objectValue, methodInfo, parameters);
            return builder.setExpression(methodValue).build();
        }

        // no value (method call on field that does not have effective value yet)
        CausesOfDelay objectValueDelayed = objectValue.causesOfDelay();
        if (objectValueDelayed.isDelayed()) {
            return delay(builder, methodInfo, concreteReturnType, objectValueDelayed);
        }

        /* before we use the evaluation context to compute values on variables, we must check whether we're actually
          executing an inlined method, see Lambda_2

          we also have to take care to take the concrete method, and not the formal functional interface
         */
        InlinedMethod inlineValue;
        if (methodInfo.typeInfo.typeInspection.get().isFunctionalInterface() &&
                (inlineValue = objectValue.asInstanceOf(InlinedMethod.class)) != null &&
                inlineValue.canBeApplied(context)) {
            Expression scopeOfObjectValue = new VariableExpression(context.evaluationContext().currentThis());
            TranslationMap translationMap = inlineValue.translationMap(context,
                    parameters, scopeOfObjectValue, context.getCurrentType(), identifier);
            Expression translated = inlineValue.translate(analyserContext, translationMap);
            ForwardEvaluationInfo fwd = new ForwardEvaluationInfo.Builder(forwardEvaluationInfo)
                    .addMethod(methodInfo).doNotComplainInlineConditional().build();
            return translated.evaluate(context, fwd);
        }

        if (TypeInfo.IS_FACT_FQN.equals(methodInfo.fullyQualifiedName) && !analyserContext.inAnnotatedAPIAnalysis()) {
            Expression parameter = parameters.get(0);
            boolean inState = inState(context, parameter);
            return builder.setExpression(new BooleanConstant(primitives, inState)).build();
        }

        if (TypeInfo.IS_KNOWN_FQN.equals(methodInfo.fullyQualifiedName) && !analyserContext.inAnnotatedAPIAnalysis() &&
                parameters.get(0) instanceof BooleanConstant boolValue) {
            BooleanConstant TRUE = new BooleanConstant(primitives, true);
            Expression clause = new MethodCall(identifier, objectValue, methodInfo, List.of(TRUE));
            if (boolValue.constant()) {
                boolean isKnown = inState(context, clause);
                Expression result = new BooleanConstant(primitives, isKnown);
                return builder.setExpression(result).build();
            }
            // isKnown(false)-> return MethodValue
            return builder.setExpression(clause).build();
        }

       /*
         if the condition contains a boolean method call expression, such as "this.contains("a")", an we are not
         in companion expression mode, then evaluating this.contains("a") will result in TRUE.
         In companion expression mode, we work symbolically, and must leave this.contains("a") as an informational clause.
         Examples: BasicCompanionMethods_5 for the negative scenario; CyclicReferences_3 for the positive one
        */
        if (modified.valueIsFalse() && !forwardEvaluationInfo.isInCompanionExpression() && methodInfo.returnType().isBoolean()) {
            Expression condition = context.evaluationContext().getConditionManager().condition();
            if (methodCall.equals(condition) || condition instanceof And and && and.getExpressions().stream().anyMatch(methodCall::equals)) {
                BooleanConstant TRUE = new BooleanConstant(context.getPrimitives(), true);
                return builder.setExpression(TRUE).build();
            }
            if (condition instanceof Negation n && methodCall.equals(n.expression) ||
                    condition instanceof And and && and.getExpressions().stream().anyMatch(methodCall::equals)) {
                BooleanConstant FALSE = new BooleanConstant(context.getPrimitives(), false);
                return builder.setExpression(FALSE).build();
            }
        }

        // static eval: Integer.toString(3)
        Expression knownStaticEvaluation = computeStaticEvaluation(methodInfo, parameters);
        if (knownStaticEvaluation != null) {
            return builder.setExpression(knownStaticEvaluation).build();
        }

        // eval on constant, like "abc".length()
        Expression evaluationOnConstant = computeEvaluationOnConstant(methodInfo, objectValue, parameters);
        if (evaluationOnConstant != null) {
            return builder.setExpression(evaluationOnConstant).build();
        }

        EvaluationResult evaluationOfEquals = computeEvaluationOfEquals(methodInfo, concreteReturnType, objectValue,
                builder, parameters);
        if (evaluationOfEquals != null) {
            return evaluationOfEquals;
        }

        if (!context.evaluationContext().disableEvaluationOfMethodCallsUsingCompanionMethods()) {
            // boolean added = set.add(e);  -- if the set is empty, we know the result will be "true"
            Expression assistedByCompanion = valueAssistedByCompanion(context, objectValue, methodInfo, methodAnalysis,
                    parameters);
            if (assistedByCompanion != null) {
                return builder.setExpression(assistedByCompanion).build();
            }
        }
        if (modified.valueIsFalse()) {
            if (!analyserContext.inAnnotatedAPIAnalysis()) {
                // new object returned, with a transfer of the aspect; 5 == stringBuilder.length() in aspect -> 5 == stringBuilder.toString().length()
                Expression newInstance = newInstanceWithTransferCompanion(context, objectValue, methodInfo,
                        methodAnalysis, parameters);
                if (newInstance != null) {
                    return builder.setExpression(newInstance).build();
                }
            }
            // evaluation with state; check companion methods
            // TYPE 1: boolean expression of aspect; e.g., xx == aspect method (5 == string.length())
            // TYPE 2: boolean clause; e.g., contains("a")
            Filter.FilterResult<MethodCall> evaluationOnInstance =
                    computeEvaluationOnInstance(context, methodInfo, objectValue, parameters);
            if (evaluationOnInstance != null && !evaluationOnInstance.accepted().isEmpty()) {
                Expression value = evaluationOnInstance.accepted().values().stream().findFirst().orElseThrow();
                return builder.setExpression(value).build();
            }
        }

        // @Identity as method annotation
        EvaluationResult identity = computeIdentity(concreteReturnType, methodAnalysis, parameters, builder);
        if (identity != null) {
            return identity;
        }

        // @Fluent as method annotation
        // fluent methods are modifying
        EvaluationResult fluent = computeFluent(concreteReturnType, methodAnalysis, objectValue, modifiedInstance,
                builder);
        if (fluent != null) {
            return fluent;
        }

        Expression nameInEnum = computeNameInEnum(objectValue);
        if (nameInEnum != null) {
            return builder.setExpression(nameInEnum).build();
        }

        if (methodAnalysis.isComputed() && !methodInfo.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle()) {
            Expression srv = methodAnalysis.getSingleReturnValue();
            if (srv.isDelayed()) {
                LOGGER.debug("Delaying method value on {}", methodInfo.fullyQualifiedName);
                return delay(builder, methodInfo, concreteReturnType, srv.causesOfDelay());
            }
            InlinedMethod iv;
            if ((iv = srv.asInstanceOf(InlinedMethod.class)) != null && iv.canBeApplied(context) &&
                    forwardEvaluationInfo.allowInline(methodInfo)) {
                TranslationMap translationMap = iv.translationMap(context, parameters, objectValue,
                        context.getCurrentType(), identifier);
                Expression translated = iv.translate(analyserContext, translationMap);
                ForwardEvaluationInfo forward = new ForwardEvaluationInfo.Builder(forwardEvaluationInfo)
                        .addMethod(methodInfo).doNotComplainInlineConditional().build();
                EvaluationResult reSrv = translated.evaluate(context, forward);
                return builder.compose(reSrv).setExpression(reSrv.value()).build();
            }
            if (srv.isConstant()) {
                return builder.setExpression(srv).build();
            }
        }

        Expression methodValue;
        if (modified.valueIsFalse()) {
            methodValue = new MethodCall(identifier, objectIsImplicit, objectValue, methodInfo, concreteReturnType, parameters);
        } else if (modified.valueIsTrue()) {
            DV notNull = methodAnalysis.getProperty(NOT_NULL_EXPRESSION).max(AnalysisProvider.defaultNotNull(concreteReturnType));
            Properties valueProperties = analyserContext.defaultValueProperties(concreteReturnType, notNull);
            CausesOfDelay delays = valueProperties.delays();
            if (delays.isDelayed()) {
                return delay(builder, methodInfo, concreteReturnType, delays);
            }
            methodValue = Instance.forMethodResult(methodCall.getIdentifier(), concreteReturnType, valueProperties);
        } else {
            return delay(builder, methodInfo, concreteReturnType, modified.causesOfDelay());
        }
        return builder.setExpression(methodValue).build();
    }

    private static boolean inState(EvaluationResult context, Expression expression) {
        Filter filter = new Filter(context, Filter.FilterMode.ACCEPT);
        Expression absoluteState = context.evaluationContext().getConditionManager().absoluteState(context);
        Filter.FilterResult<Expression> res = filter.filter(absoluteState, new Filter.ExactValue(filter.getDefaultRest(), expression));
        return !res.accepted().isEmpty();
    }

    /*
    IMPORTANT: keep in sync with very similar method in MethodCall
    Make sure that all delayed exits go via this method!
     */
    private EvaluationResult delay(EvaluationResult.Builder builder,
                                   MethodInfo methodInfo,
                                   ParameterizedType concreteReturnType,
                                   CausesOfDelay causesOfDelay) {
        LinkedVariables linkedVariables = methodCall.linkedVariables(context);
        Map<Variable, DV> cnnMap = builder.cnnMap();
        return builder.setExpression(DelayedExpression.forMethod(identifier, methodInfo, concreteReturnType,
                linkedVariables, causesOfDelay, cnnMap)).build();
    }

    /*
    dedicated code, because we cannot easily provide an inspected block in EnumMethods

    name is always dedicated.
    valueOf is only dedicated when there are no annotated APIs (it "implementation" uses the Stream class)
     */
    private Expression computeNameInEnum(Expression objectValue) {
        boolean isName = "name".equals(methodInfo.name);
        boolean isValueOf = "valueOf".equals(methodInfo.name);
        if (!isName && !isValueOf) return null;
        TypeInspection typeInspection = analyserContext.getTypeInspection(methodInfo.typeInfo);
        if (typeInspection.typeNature() != TypeNature.ENUM) return null;
        if (isName) {
            VariableExpression ve;
            if ((ve = objectValue.asInstanceOf(VariableExpression.class)) != null
                    && ve.variable() instanceof FieldReference fr
                    && fr.fieldInfo.owner == methodInfo.typeInfo) {
                return new StringConstant(primitives, fr.fieldInfo.name);
            }
            Properties valueProperties = EvaluationContext.PRIMITIVE_VALUE_PROPERTIES;
            return Instance.forGetInstance(identifier, primitives.stringParameterizedType(), valueProperties);
        }
        MethodInspection methodInspection = analyserContext.getMethodInspection(methodInfo);
        if (methodInspection.getMethodBody().structure.haveStatements()) {
            return null; // implementation present
        }
        // no implementation, we'll provide something (we could actually implement the method, but why?)
        ParameterizedType parameterizedType = objectValue.returnType();
        Properties valueProperties = analyserContext.defaultValueProperties(parameterizedType,
                MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        CausesOfDelay delayed = valueProperties.delays();
        if (delayed.isDelayed()) {
            return DelayedExpression.forValueOf(parameterizedType, delayed);
        }
        return Instance.forGetInstance(identifier, parameterizedType, valueProperties);
    }

    private EvaluationResult computeEvaluationOfEquals(MethodInfo methodInfo,
                                                       ParameterizedType concreteReturnType,
                                                       Expression objectValue,
                                                       EvaluationResult.Builder builder,
                                                       List<Expression> parameters) {
        if ("equals".equals(methodInfo.name) && parameters.size() == 1) {
            Expression paramValue = parameters.get(0);
            DV modifying = modifying(paramValue);
            if (modifying.isDelayed()) {
                LOGGER.debug("Delaying method value because @Modified delayed on {}",
                        methodInfo.fullyQualifiedName);
                return delay(builder, methodInfo, concreteReturnType, modifying.causesOfDelay());
            }
            if (paramValue.equals(objectValue) && modifying.valueIsFalse()) {
                return builder.setExpression(new BooleanConstant(primitives, true)).build();
            }
        }
        return null;
    }

    private DV modifying(Expression expression) {
        if (expression instanceof MethodCall) {
            return context.evaluationContext().getProperty(expression, Property.MODIFIED_METHOD, false, false);
        }
        return DV.FALSE_DV;
    }

    private Expression valueAssistedByCompanion(EvaluationResult context,
                                                Expression objectValue,
                                                MethodInfo methodInfo,
                                                MethodAnalysis methodAnalysis,
                                                List<Expression> parameterValues) {
        if (!context.evaluationContext().hasState(objectValue)) return null;
        Expression state = context.evaluationContext().state(objectValue);

        Optional<Map.Entry<CompanionMethodName, CompanionAnalysis>> optValue = methodAnalysis.getCompanionAnalyses()
                .entrySet().stream()
                .filter(e -> e.getKey().action() == CompanionMethodName.Action.VALUE)
                .findFirst();
        if (optValue.isEmpty()) {
            return null;
        }
        CompanionAnalysis companionAnalysis = optValue.get().getValue();
        Expression companionValue = companionAnalysis.getValue();
        TranslationMapImpl.Builder builder = new TranslationMapImpl.Builder();

        // parameters of companionAnalysis look like: aspect (if present) | main method parameters | retVal
        // the aspect has been replaced+taken care of by the CompanionAnalyser
        // we must put a replacement in the translation map for each of the parameters
        // we do not bother with the retVal (which is of type VariableValue(ReturnVariable))
        ListUtil.joinLists(companionAnalysis.getParameterValues(), parameterValues)
                .forEach(pair -> builder.put(pair.k, pair.v));
        Expression translated = companionValue.translate(context.getAnalyserContext(), builder.build());
        // we might encounter isFact or isKnown, so we add the instance's state to the context
        EvaluationResult child = context.child(state, true);
        ForwardEvaluationInfo fwd = new ForwardEvaluationInfo.Builder().doNotReevaluateVariableExpressionsDoNotComplain()
                .setInCompanionExpression().build();
        Expression resultingValue = translated.evaluate(child, fwd).value();

        if (state != EmptyExpression.EMPTY_EXPRESSION && resultingValue != EmptyExpression.EMPTY_EXPRESSION) {
            if (methodInfo.returnType().typeInfo.isBoolean()) {
                // State is: (org.e2immu.annotatedapi.AnnotatedAPI.this.isKnown(true) and 0 == java.util.Collection.this.size())
                // Resulting value: (java.util.Set.contains(java.lang.Object) and not (0 == java.util.Collection.this.size()))
                Expression reduced = And.and(context, state, resultingValue);
                if (reduced instanceof BooleanConstant) {
                    return reduced;
                }
                if (reduced.equals(state)) {
                    // only truths have been added
                    return new BooleanConstant(primitives, true);
                }
            } else if (resultingValue instanceof InlineConditional || resultingValue instanceof And) {
                // resulting value is expected to be an inline operator, its condition to be combined with the instance state
                return resultingValue;

            } else {
                throw new UnsupportedOperationException();
            }
            // unsuccessful
        }
        return null;
    }

    private Expression newInstanceWithTransferCompanion(EvaluationResult context,
                                                        Expression objectValue,
                                                        MethodInfo methodInfo,
                                                        MethodAnalysis methodAnalysis,
                                                        List<Expression> parameterValues) {
        if (!context.evaluationContext().hasState(objectValue)) return null;
        Expression state = context.evaluationContext().state(objectValue);

        TranslationMapImpl.Builder translationMap = new TranslationMapImpl.Builder();
        methodAnalysis.getCompanionAnalyses().entrySet().stream()
                .filter(e -> e.getKey().action() == CompanionMethodName.Action.TRANSFER && e.getKey().aspect() != null)
                .forEach(e -> {
                    // we're assuming the aspects retain their name, but apart from the name we allow them to be different methods
                    CompanionMethodName cmn = e.getKey();
                    MethodInfo oldAspectMethod = analyserContext
                            .getTypeAnalysis(objectValue.returnType().typeInfo).getAspects().get(cmn.aspect());
                    Expression oldValue = new MethodCall(identifier,
                            new VariableExpression(new This(analyserContext, oldAspectMethod.typeInfo)),
                            oldAspectMethod, List.of());
                    MethodInfo newAspectMethod = analyserContext
                            .getTypeAnalysis(methodInfo.typeInfo).getAspects().get(cmn.aspect());
                    Expression newValue = new MethodCall(identifier,
                            new VariableExpression(new This(analyserContext, newAspectMethod.typeInfo)),
                            newAspectMethod, List.of());
                    translationMap.put(oldValue, newValue);
                    CompanionAnalysis companionAnalysis = e.getValue();
                    ListUtil.joinLists(companionAnalysis.getParameterValues(), parameterValues)
                            .forEach(pair -> translationMap.put(pair.k, pair.v));
                });

        if (translationMap.isEmpty()) return null;
        Expression translated = state.translate(context.getAnalyserContext(), translationMap.build());
        ForwardEvaluationInfo fwd = new ForwardEvaluationInfo.Builder().doNotReevaluateVariableExpressionsDoNotComplain()
                .setInCompanionExpression().build();
        Expression newState = translated.evaluate(context, fwd).value();

        DV notNull = MultiLevel.EFFECTIVELY_NOT_NULL_DV.max(methodAnalysis.getProperty(NOT_NULL_EXPRESSION));
        return PropertyWrapper.addState(methodCall, newState, Map.of(NOT_NULL_EXPRESSION, notNull));
    }

    // example 1: instance type java.util.ArrayList()[0 == java.util.ArrayList.this.size()].size()
    // this approach is independent of the companion methods: it simply searches for clauses related to the method
    // in the instance state
    private Filter.FilterResult<MethodCall> computeEvaluationOnInstance(EvaluationResult context,
                                                                        MethodInfo methodInfo,
                                                                        Expression objectValue,
                                                                        List<Expression> parameterValues) {
        if (!context.evaluationContext().hasState(objectValue)) return null;
        Expression state = context.evaluationContext().state(objectValue);
        // look for a clause that has "this.methodInfo" as a MethodValue
        return filter(context, methodInfo, state, parameterValues);
    }

    // also used separately in MethodCall
    public static Filter.FilterResult<MethodCall> filter(
            EvaluationResult context,
            MethodInfo methodInfo,
            Expression state,
            List<Expression> parameterValues) {
        Filter filter = new Filter(context, Filter.FilterMode.ACCEPT);
        List<Filter.FilterMethod<MethodCall>> filters = List.of(
                new Filter.MethodCallBooleanResult(filter.getDefaultRest(), methodInfo, parameterValues,
                        new BooleanConstant(context.getPrimitives(), true)),
                new Filter.ValueEqualsMethodCallNoParameters(filter.getDefaultRest(), methodInfo));
        return filter.filter(state, filters);
    }

    private Expression computeStaticEvaluation(MethodInfo methodInfo, List<Expression> parameters) {
        if ("java.lang.Integer.toString(int)".equals(methodInfo.fullyQualifiedName()) &&
                parameters.get(0).isConstant()) {
            return new StringConstant(primitives, Integer.toString(((IntConstant) parameters.get(0)).constant()));
        }
        return null;
    }

    private Expression computeEvaluationOnConstant(MethodInfo methodInfo, Expression objectValue, List<Expression> params) {
        if (!objectValue.isConstant()) return null;
        StringConstant stringValue;
        if ("java.lang.String.length()".equals(methodInfo.fullyQualifiedName()) &&
                (stringValue = objectValue.asInstanceOf(StringConstant.class)) != null) {
            return new IntConstant(primitives, stringValue.constant().length());
        }
        if ("equals".equals(methodInfo.name) && params.size() == 1 && params.get(0) instanceof ConstantExpression ce) {
            return new BooleanConstant(primitives, objectValue.equals(ce));
        }
        return null;
    }

    private EvaluationResult computeFluent(ParameterizedType concreteReturnType,
                                           MethodAnalysis methodAnalysis,
                                           Expression scope,
                                           Expression modifiedInstance,
                                           EvaluationResult.Builder builder) {
        DV fluent = methodAnalysis.getProperty(Property.FLUENT);
        if (fluent.isDelayed() && methodAnalysis.isNotContracted()) {
            return delay(builder, methodInfo, concreteReturnType, fluent.causesOfDelay());
        }
        if (!fluent.valueIsTrue()) return null;
        Expression toReturn = modifiedInstance != null ? modifiedInstance : scope;
        DV hardCoded = toReturn.hardCodedPropertyOrNull(NOT_NULL_EXPRESSION);
        Expression e;
        if (hardCoded != null && hardCoded.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) {
            e = toReturn;
        } else {
            e = PropertyWrapper.propertyWrapper(toReturn, Map.of(NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV));
        }
        return builder.setExpression(e).build();
    }


    private final static Property[] PROPERTIES_IN_METHOD_RESULT_WRAPPER = {NOT_NULL_EXPRESSION};

    private EvaluationResult computeIdentity(ParameterizedType concreteReturnType,
                                             MethodAnalysis methodAnalysis,
                                             List<Expression> parameters,
                                             EvaluationResult.Builder builder) {
        DV identity = methodAnalysis.getProperty(Property.IDENTITY);
        if (identity.isDelayed() && methodAnalysis.isNotContracted()) {
            return delay(builder, methodInfo, concreteReturnType, identity.causesOfDelay());
        }
        if (!identity.valueIsTrue()) return null;

        Expression parameter = parameters.get(0);
        Map<Property, DV> map = new HashMap<>();
        for (Property property : PROPERTIES_IN_METHOD_RESULT_WRAPPER) {
            DV v = methodAnalysis.getProperty(property);
            DV p = context.evaluationContext().getProperty(parameter, property, true, true);
            if (v.isDone() && !v.equals(p)) map.put(property, v);
        }
        Expression e = map.isEmpty() ? parameter : PropertyWrapper.propertyWrapper(parameter, map);
        return builder.setExpression(e).build();
    }

}
