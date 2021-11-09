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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.Logger;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.VariableProperty.*;
import static org.e2immu.analyser.util.Logger.log;

public class EvaluateMethodCall {

    private final EvaluationContext evaluationContext;
    private final AnalyserContext analyserContext;
    private final Primitives primitives;
    private final Identifier identifier;
    private final MethodCall methodCall;
    private final MethodInfo methodInfo;

    public EvaluateMethodCall(EvaluationContext evaluationContext, MethodCall methodCall) {
        this.evaluationContext = Objects.requireNonNull(evaluationContext);
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
                                        List<Expression> parameters) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        boolean recursiveCall = evaluationContext.getCurrentMethod() != null &&
                evaluationContext.getCurrentMethod().methodInfo == methodInfo;
        if (recursiveCall) {
            MethodCall methodValue = new MethodCall(identifier, objectValue, methodInfo, parameters);
            return builder.setExpression(methodValue).build();
        }

        Function<CausesOfDelay, LinkedVariables> linkedVariablesForDelay =
                causes -> objectValue.linkedVariables(evaluationContext).changeAllToDelay(causes);

        // no value (method call on field that does not have effective value yet)
        CausesOfDelay objectValueDelayed = objectValue.causesOfDelay();
        if (objectValueDelayed.isDelayed()) {
            return delay(builder, methodInfo, concreteReturnType, linkedVariablesForDelay.apply(objectValueDelayed), objectValueDelayed);
        }

        /* before we use the evaluation context to compute values on variables, we must check whether we're actually
          executing an inlined method

          we also have to take care to take the concrete method, and not the formal functional interface
         */
        InlinedMethod inlineValue;
        if (methodInfo.typeInfo.typeInspection.get().isFunctionalInterface() &&
                (inlineValue = objectValue.asInstanceOf(InlinedMethod.class)) != null &&
                inlineValue.canBeApplied(evaluationContext)) {
            Map<Expression, Expression> translationMap = inlineValue.translationMap(evaluationContext,
                    parameters, objectValue, evaluationContext.getCurrentType(), identifier);
            return inlineValue.reEvaluate(evaluationContext, translationMap);
        }

        if (AnnotatedAPIAnalyser.IS_KNOWN_FQN.equals(methodInfo.fullyQualifiedName) &&
                !analyserContext.inAnnotatedAPIAnalysis() &&
                parameters.get(0) instanceof BooleanConstant boolValue) {
            Expression clause = new MethodCall(identifier, objectValue, methodInfo,
                    List.of(new BooleanConstant(primitives, true)));
            if (boolValue.constant()) {
                Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
                // isKnown(true) -> return BoolValue.TRUE or BoolValue.FALSE, depending on state
                Expression absoluteState = evaluationContext.getConditionManager().absoluteState(evaluationContext);
                Filter.FilterResult<Expression> res = filter.filter(absoluteState,
                        new Filter.ExactValue(filter.getDefaultRest(), clause));
                boolean isKnown = !res.accepted().isEmpty();
                Expression result = new BooleanConstant(primitives, isKnown);
                return builder.setExpression(result).build();
            } else {
                // isKnown(false)-> return MethodValue
                return builder.setExpression(clause).build();
            }
        }

        // static eval: Integer.toString(3)
        Expression knownStaticEvaluation = computeStaticEvaluation(methodInfo, parameters);
        if (knownStaticEvaluation != null) {
            return builder.setExpression(knownStaticEvaluation).build();
        }

        // eval on constant, like "abc".length()
        Expression evaluationOnConstant = computeEvaluationOnConstant(methodInfo, objectValue);
        if (evaluationOnConstant != null) {
            return builder.setExpression(evaluationOnConstant).build();
        }

        Expression evaluationOfEquals = computeEvaluationOfEquals(methodInfo, concreteReturnType, objectValue,
                linkedVariablesForDelay, parameters);
        if (evaluationOfEquals != null) {
            return builder.setExpression(evaluationOfEquals).build();
        }

        if (!evaluationContext.disableEvaluationOfMethodCallsUsingCompanionMethods()) {
            // boolean added = set.add(e);  -- if the set is empty, we know the result will be "true"
            Expression assistedByCompanion = valueAssistedByCompanion(evaluationContext, objectValue, methodInfo, methodAnalysis,
                    parameters);
            if (assistedByCompanion != null) {
                return builder.setExpression(assistedByCompanion).build();
            }
        }
        if (modified.valueIsFalse()) {
            if (!analyserContext.inAnnotatedAPIAnalysis()) {
                // new object returned, with a transfer of the aspect; 5 == stringBuilder.length() in aspect -> 5 == stringBuilder.toString().length()
                Expression newInstance = newInstanceWithTransferCompanion(evaluationContext, objectValue, methodInfo,
                        methodAnalysis, parameters);
                if (newInstance != null) {
                    return builder.setExpression(newInstance).build();
                }
            }
            // evaluation with state; check companion methods
            // TYPE 1: boolean expression of aspect; e.g., xx == aspect method (5 == string.length())
            // TYPE 2: boolean clause; e.g., contains("a")
            Filter.FilterResult<MethodCall> evaluationOnInstance =
                    computeEvaluationOnInstance(evaluationContext, methodInfo, objectValue, parameters);
            if (evaluationOnInstance != null && !evaluationOnInstance.accepted().isEmpty()) {
                Expression value = evaluationOnInstance.accepted().values().stream().findFirst().orElseThrow();
                return builder.setExpression(value).build();
            }
        }

        // @Identity as method annotation
        Expression identity = computeIdentity(methodInfo, concreteReturnType,
                methodAnalysis, parameters, linkedVariablesForDelay, evaluationContext);
        if (identity != null) {
            return builder.setExpression(identity).build();
        }

        // @Fluent as method annotation
        // fluent methods are modifying
        Expression fluent = computeFluent(methodInfo, concreteReturnType, methodAnalysis, objectValue, linkedVariablesForDelay);
        if (fluent != null) {
            return builder.setExpression(fluent).build();
        }

        Expression nameInEnum = computeNameInEnum(objectValue);
        if (nameInEnum != null) {
            return builder.setExpression(nameInEnum).build();
        }

        if (methodAnalysis.isComputed() && !methodInfo.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle()) {
            Expression srv = methodAnalysis.getSingleReturnValue();
            if (srv.isDone()) {
                // if this method was identity?

                InlinedMethod iv;
                if ((iv = srv.asInstanceOf(InlinedMethod.class)) != null && iv.canBeApplied(evaluationContext)) {
                    Map<Expression, Expression> translationMap = iv.translationMap(evaluationContext,
                            parameters, objectValue, evaluationContext.getCurrentType(), identifier);
                    EvaluationResult reSrv = iv.reEvaluate(evaluationContext, translationMap);
                    return builder.compose(reSrv).setExpression(reSrv.value()).build();
                }
                if (srv.isConstant()) {
                    return builder.setExpression(srv).build();
                }
            } else {
                // we will, at some point, analyse this method, but in case of cycles, this is a bit risky
                log(Logger.LogTarget.DELAYED, "Delaying method value on {}", methodInfo.fullyQualifiedName);
                return delay(builder, methodInfo, concreteReturnType, linkedVariablesForDelay.apply(srv.causesOfDelay()),
                        srv.causesOfDelay());
            }
        }

        Expression methodValue = switch (modified.value()) {
            case Level.FALSE -> new MethodCall(identifier, objectIsImplicit, objectValue, methodInfo,
                    concreteReturnType, parameters);
            case Level.TRUE -> {
                DV notNull = methodAnalysis.getProperty(NOT_NULL_EXPRESSION);
                DV immutable = methodAnalysis.getProperty(IMMUTABLE);
                DV independent = methodAnalysis.getProperty(INDEPENDENT);
                DV container = methodAnalysis.getProperty(CONTAINER);
                CausesOfDelay delays = notNull.causesOfDelay().merge(immutable.causesOfDelay()).merge(independent.causesOfDelay())
                        .merge(container.causesOfDelay());
                if (delays.isDelayed()) {
                    yield DelayedExpression.forMethod(methodInfo, concreteReturnType, linkedVariablesForDelay.apply(delays), delays);
                }

                Map<VariableProperty, DV> valueProperties = Map.of(NOT_NULL_EXPRESSION, notNull,
                        IMMUTABLE, immutable, INDEPENDENT, independent, CONTAINER, container, IDENTITY, Level.FALSE_DV);
                yield Instance.forMethodResult(Identifier.joined(ListUtil.immutableConcat(
                                List.of(methodInfo.identifier, objectValue.getIdentifier()),
                                parameters.stream().map(Expression::getIdentifier).toList())),
                        concreteReturnType, valueProperties);
            }
            default -> DelayedExpression.forMethod(methodInfo, concreteReturnType, linkedVariablesForDelay.apply(modified.causesOfDelay()),
                    modified.causesOfDelay());
        };
        return builder.setExpression(methodValue).build();
    }

    private static EvaluationResult delay(EvaluationResult.Builder builder,
                                          MethodInfo methodInfo,
                                          ParameterizedType concreteReturnType,
                                          LinkedVariables linkedVariables,
                                          CausesOfDelay causesOfDelay) {
        return builder.setExpression(DelayedExpression.forMethod(methodInfo, concreteReturnType, linkedVariables, causesOfDelay))
                .build();
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
            Map<VariableProperty, DV> valueProperties = Instance.primitiveValueProperties();
            return Instance.forGetInstance(identifier, primitives.stringParameterizedType, valueProperties);
        }
        MethodInspection methodInspection = analyserContext.getMethodInspection(methodInfo);
        if (methodInspection.getMethodBody().structure.haveStatements()) {
            return null; // implementation present
        }
        // no implementation, we'll provide something (we could actually implement the method, but why?)
        ParameterizedType parameterizedType = objectValue.returnType();
        DV immutable = parameterizedType.defaultImmutable(analyserContext, false);
        DV independent = parameterizedType.defaultIndependent(analyserContext);
        DV container = parameterizedType.defaultContainer(analyserContext);
        if (immutable.isDelayed() || independent.isDelayed() || container.isDelayed()) {
            return DelayedExpression.forValueOf(parameterizedType, immutable.causesOfDelay()
                    .merge(independent.causesOfDelay()).merge(container.causesOfDelay()));
        }
        Map<VariableProperty, DV> valueProperties = Map.of(NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                IMMUTABLE, immutable, INDEPENDENT, independent, CONTAINER, container, IDENTITY, Level.FALSE_DV);
        return Instance.forGetInstance(identifier, parameterizedType, valueProperties);
    }

    /*
    special situation
    We have an instance object, like new Pair("a", "b"), and then a getter applying to this instance object
    this we can resolve immediately

    See also InlinedMethod which has a similar method
    */
    private EvaluationResult tryEvaluationShortCut(EvaluationResult.Builder builder,
                                                   Expression objectValue,
                                                   LinkedVariables linkedVariables,
                                                   InlinedMethod iv) {
        ConstructorCall constructorCall;
        VariableExpression varEx;
        ConstructorCall cc;
        if ((cc = objectValue.asInstanceOf(ConstructorCall.class)) != null) constructorCall = cc;
        else if ((varEx = objectValue.asInstanceOf(VariableExpression.class)) != null
                && varEx.variable() instanceof FieldReference fieldReference) {
            FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
            ConstructorCall cc2;
            if ((cc2 = fieldAnalysis.getEffectivelyFinalValue().asInstanceOf(ConstructorCall.class)) != null) {
                constructorCall = cc2;
            } else {
                return null;
            }
        } else {
            return null;
        }
        VariableExpression ve;
        if ((ve = iv.expression().asInstanceOf(VariableExpression.class)) != null && constructorCall.constructor() != null) {
            Variable variable = ve.variable();
            if (variable instanceof FieldReference) {
                FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldInfo);
                if (fieldAnalysis.getProperty(VariableProperty.FINAL).valueIsTrue()) {

                    int i = 0;
                    List<ParameterAnalysis> parameterAnalyses = evaluationContext
                            .getParameterAnalyses(constructorCall.constructor()).collect(Collectors.toList());
                    for (ParameterAnalysis parameterAnalysis : parameterAnalyses) {
                        if (!parameterAnalysis.assignedToFieldIsFrozen()) {
                            return builder.setExpression(DelayedExpression.forMethod(iv.methodInfo(),
                                            iv.expression().returnType(), linkedVariables,
                                            new CausesOfDelay.SimpleSet(
                                                    new CauseOfDelay.SimpleCause(parameterAnalysis.location(),
                                                            CauseOfDelay.Cause.ASSIGNED_TO_FIELD))))
                                    .build();
                        }
                        Map<FieldInfo, DV> assigned = parameterAnalysis.getAssignedToField();
                        DV assignedOrLinked = assigned.get(fieldInfo);
                        if (LinkedVariables.isAssigned(assignedOrLinked)) {
                            return builder.setExpression(constructorCall.getParameterExpressions().get(i)).build();
                        }
                        i++;
                    }
                }
            }
        }
        return null;
    }

    private Expression computeEvaluationOfEquals(MethodInfo methodInfo,
                                                 ParameterizedType concreteReturnType,
                                                 Expression objectValue,
                                                 Function<CausesOfDelay, LinkedVariables> linkedVariables,
                                                 List<Expression> parameters) {
        if ("equals".equals(methodInfo.name) && parameters.size() == 1) {
            Expression paramValue = parameters.get(0);
            DV modifying = modifying(paramValue);
            if (modifying.isDelayed()) {
                log(Logger.LogTarget.DELAYED, "Delaying method value because @Modified delayed on {}",
                        methodInfo.fullyQualifiedName);
                return DelayedExpression.forMethod(methodInfo, concreteReturnType, linkedVariables.apply(modifying.causesOfDelay()),
                        modifying.causesOfDelay());
            }
            if (paramValue.equals(objectValue) && modifying.valueIsFalse()) {
                return new BooleanConstant(primitives, true);
            }
        }
        return null;
    }

    private DV modifying(Expression expression) {
        if (expression instanceof MethodCall) {
            return evaluationContext.getProperty(expression, VariableProperty.MODIFIED_METHOD, false, false);
        }
        return Level.FALSE_DV;
    }

    private Expression valueAssistedByCompanion(EvaluationContext evaluationContext,
                                                Expression objectValue,
                                                MethodInfo methodInfo,
                                                MethodAnalysis methodAnalysis,
                                                List<Expression> parameterValues) {
        if (!evaluationContext.hasState(objectValue)) return null;
        Expression state = evaluationContext.state(objectValue);

        Optional<Map.Entry<CompanionMethodName, CompanionAnalysis>> optValue = methodAnalysis.getCompanionAnalyses()
                .entrySet().stream()
                .filter(e -> e.getKey().action() == CompanionMethodName.Action.VALUE)
                .findFirst();
        if (optValue.isEmpty()) {
            return null;
        }
        CompanionAnalysis companionAnalysis = optValue.get().getValue();
        Expression companionValue = companionAnalysis.getValue();
        Map<Expression, Expression> translationMap = new HashMap<>();

        // parameters of companionAnalysis look like: aspect (if present) | main method parameters | retVal
        // the aspect has been replaced+taken care of by the CompanionAnalyser
        // we must put a replacement in the translation map for each of the parameters
        // we do not bother with the retVal (which is of type VariableValue(ReturnVariable))
        ListUtil.joinLists(companionAnalysis.getParameterValues(), parameterValues)
                .forEach(pair -> translationMap.put(pair.k, pair.v));
        // we might encounter isFact or isKnown, so we add the instance's state to the context
        EvaluationContext child = evaluationContext.child(state, true);
        Expression resultingValue = companionValue.reEvaluate(child, translationMap).value();

        if (state != EmptyExpression.EMPTY_EXPRESSION && resultingValue != EmptyExpression.EMPTY_EXPRESSION) {
            if (Primitives.isBoolean(methodInfo.returnType().typeInfo)) {
                // State is: (org.e2immu.annotatedapi.AnnotatedAPI.this.isKnown(true) and 0 == java.util.Collection.this.size())
                // Resulting value: (java.util.Set.contains(java.lang.Object) and not (0 == java.util.Collection.this.size()))
                Expression reduced = And.and(evaluationContext, state, resultingValue);
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

    private Expression newInstanceWithTransferCompanion(EvaluationContext evaluationContext,
                                                        Expression objectValue,
                                                        MethodInfo methodInfo,
                                                        MethodAnalysis methodAnalysis,
                                                        List<Expression> parameterValues) {
        if (!evaluationContext.hasState(objectValue)) return null;
        Expression state = evaluationContext.state(objectValue);

        Map<Expression, Expression> translationMap = new HashMap<>();
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
        Expression newState = state.reEvaluate(evaluationContext, translationMap).value();

        DV notNull = MultiLevel.EFFECTIVELY_NOT_NULL_DV.max(methodAnalysis.getProperty(NOT_NULL_EXPRESSION));
        return PropertyWrapper.addState(methodCall, newState, Map.of(NOT_NULL_EXPRESSION, notNull));
    }

    // example 1: instance type java.util.ArrayList()[0 == java.util.ArrayList.this.size()].size()
    // this approach is independent of the companion methods: it simply searches for clauses related to the method
    // in the instance state
    private Filter.FilterResult<MethodCall> computeEvaluationOnInstance(EvaluationContext evaluationContext,
                                                                        MethodInfo methodInfo,
                                                                        Expression objectValue,
                                                                        List<Expression> parameterValues) {
        if (!evaluationContext.hasState(objectValue)) return null;
        Expression state = evaluationContext.state(objectValue);
        // look for a clause that has "this.methodInfo" as a MethodValue
        return filter(evaluationContext, methodInfo, state, parameterValues);
    }

    // also used separately in MethodCall
    public static Filter.FilterResult<MethodCall> filter(
            EvaluationContext evaluationContext,
            MethodInfo methodInfo,
            Expression state,
            List<Expression> parameterValues) {
        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
        List<Filter.FilterMethod<MethodCall>> filters = List.of(
                new Filter.MethodCallBooleanResult(filter.getDefaultRest(), methodInfo, parameterValues,
                        new BooleanConstant(evaluationContext.getPrimitives(), true)),
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

    private Expression computeEvaluationOnConstant(MethodInfo methodInfo, Expression objectValue) {
        if (!objectValue.isConstant()) return null;
        StringConstant stringValue;
        if ("java.lang.String.length()".equals(methodInfo.fullyQualifiedName()) &&
                (stringValue = objectValue.asInstanceOf(StringConstant.class)) != null) {
            return new IntConstant(primitives, stringValue.constant().length());
        }
        return null;
    }

    private static Expression computeFluent(MethodInfo methodInfo,
                                            ParameterizedType concreteReturnType,
                                            MethodAnalysis methodAnalysis,
                                            Expression scope,
                                            Function<CausesOfDelay, LinkedVariables> linkedVariables) {
        DV fluent = methodAnalysis.getProperty(VariableProperty.FLUENT);
        if (fluent.isDelayed() && methodAnalysis.isNotContracted()) {
            return DelayedExpression.forMethod(methodInfo, concreteReturnType, linkedVariables.apply(fluent.causesOfDelay()),
                    fluent.causesOfDelay());
        }
        if (!fluent.valueIsTrue()) return null;
        return scope;
    }


    private final static VariableProperty[] PROPERTIES_IN_METHOD_RESULT_WRAPPER = {NOT_NULL_EXPRESSION, IMMUTABLE};

    private static Expression computeIdentity(MethodInfo methodInfo,
                                              ParameterizedType concreteReturnType,
                                              MethodAnalysis methodAnalysis,
                                              List<Expression> parameters,
                                              Function<CausesOfDelay, LinkedVariables> linkedVariables,
                                              EvaluationContext evaluationContext) {
        DV identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
        if (identity.isDelayed() && methodAnalysis.isNotContracted()) {
            return DelayedExpression.forMethod(methodInfo, concreteReturnType, linkedVariables.apply(identity.causesOfDelay()),
                    identity.causesOfDelay());
        }
        if (!identity.valueIsTrue()) return null;

        Expression parameter = parameters.get(0);
        Map<VariableProperty, DV> map = new HashMap<>();
        for (VariableProperty property : PROPERTIES_IN_METHOD_RESULT_WRAPPER) {
            DV v = methodAnalysis.getProperty(property);
            DV p = evaluationContext.getProperty(parameter, property, true, true);
            if (v.isDone() && v.value() != p.value()) map.put(property, v);
        }
        return PropertyWrapper.propertyWrapper(parameter, map);
    }

}
