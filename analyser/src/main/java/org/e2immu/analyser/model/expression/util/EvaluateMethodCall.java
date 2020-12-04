/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.model.expression.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.*;
import java.util.stream.Collectors;

public class EvaluateMethodCall {

    private EvaluateMethodCall() {
        throw new UnsupportedOperationException();
    }

    // static, also used in MethodValue re-evaluation
    public static EvaluationResult methodValue(int modified,
                                               EvaluationContext evaluationContext,
                                               MethodInfo methodInfo,
                                               MethodAnalysis methodAnalysis,
                                               Expression objectValue,
                                               List<Expression> parameters,
                                               ObjectFlow objectFlowOfResult) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

        Objects.requireNonNull(evaluationContext);

        // no value (method call on field that does not have effective value yet)
        if (objectValue == EmptyExpression.NO_VALUE) {
            return builder.setExpression(EmptyExpression.NO_VALUE).build(); // this will delay
        }

        if (ShallowTypeAnalyser.IS_KNOWN_FQN.equals(methodInfo.fullyQualifiedName) &&
                !evaluationContext.getAnalyserContext().inAnnotatedAPIAnalysis() &&
                parameters.get(0) instanceof BooleanConstant boolValue) {
            Expression object = new VariableExpression(new This(evaluationContext.getAnalyserContext(), methodInfo.typeInfo));
            Expression clause = new MethodCall(object, methodInfo,
                    List.of(new BooleanConstant(evaluationContext.getPrimitives(), true)), objectFlowOfResult);
            if (boolValue.constant()) {
                // isKnown(true) -> return BoolValue.TRUE or BoolValue.FALSE, depending on state
                Filter.FilterResult<Expression> res = Filter.filter(evaluationContext, evaluationContext.getConditionManager().state,
                        Filter.FilterMode.ACCEPT, new Filter.ExactValue(clause));
                boolean isKnown = !res.accepted().isEmpty();
                Expression result = new BooleanConstant(evaluationContext.getPrimitives(), isKnown);
                return builder.setExpression(result).build();
            } else {
                // isKnown(false)-> return MethodValue
                return builder.setExpression(clause).build();
            }
        }

        // static eval: Integer.toString(3)
        Expression knownStaticEvaluation = computeStaticEvaluation(evaluationContext.getPrimitives(), methodInfo, parameters);
        if (knownStaticEvaluation != null) {
            return builder.setExpression(knownStaticEvaluation).build();
        }

        // eval on constant, like "abc".length()
        Expression evaluationOnConstant = computeEvaluationOnConstant(evaluationContext.getPrimitives(),
                methodInfo, objectValue);
        if (evaluationOnConstant != null) {
            return builder.setExpression(evaluationOnConstant).build();
        }

        if (!evaluationContext.disableEvaluationOfMethodCallsUsingCompanionMethods()) {
            // boolean added = set.add(e);  -- if the set is empty, we know the result will be "true"
            Expression assistedByCompanion = valueAssistedByCompanion(builder, evaluationContext,
                    objectValue, methodInfo, methodAnalysis, parameters);
            if (assistedByCompanion != null) {
                return builder.setExpression(assistedByCompanion).build();
            }
        }
        if (modified == Level.FALSE) {
            if (!evaluationContext.getAnalyserContext().inAnnotatedAPIAnalysis()) {
                // new object returned, with a transfer of the aspect; 5 == stringBuilder.length() in aspect -> 5 == stringBuilder.toString().length()
                Expression newInstance = newInstanceWithTransferCompanion(builder, evaluationContext, objectValue, methodInfo, methodAnalysis, parameters);
                if (newInstance != null) {
                    return builder.setExpression(newInstance).build();
                }
            }
            // evaluation on Instance, with state; check companion methods
            // TYPE 1: boolean expression of aspect; e.g., xx == aspect method (5 == string.length())
            // TYPE 2: boolean clause; e.g., contains("a")
            Filter.FilterResult<MethodCall> evaluationOnInstance =
                    computeEvaluationOnInstance(builder, evaluationContext, methodInfo, objectValue, parameters);
            if (evaluationOnInstance != null && !evaluationOnInstance.accepted().isEmpty()) {
                Expression value = evaluationOnInstance.accepted().values().stream().findFirst().orElseThrow();
                return builder.setExpression(value).build();
            }
        }


        // @Identity as method annotation
        Expression identity = computeIdentity(evaluationContext, methodAnalysis, parameters, objectFlowOfResult);
        if (identity != null) {
            return builder.setExpression(identity).build();
        }

        // @Fluent as method annotation
        // fluent methods are modifying
        Expression fluent = computeFluent(methodAnalysis, objectValue);
        if (fluent != null) {
            return builder.setExpression(fluent).build();
        }

        InlinedMethod inlineValue;
        if (methodInfo.typeInfo.typeInspection.get().isFunctionalInterface() &&
                (inlineValue = objectValue.asInstanceOf(InlinedMethod.class)) != null &&
                inlineValue.canBeApplied(evaluationContext)) {
            Map<Expression, Expression> translationMap = EvaluateParameters.translationMap(evaluationContext, methodInfo, parameters);
            EvaluationResult reInline = inlineValue.reEvaluate(evaluationContext, translationMap);
            return builder.compose(reInline).setExpression(reInline.value).build();
        }

        // singleReturnValue implies non-modifying
        if (methodAnalysis.isBeingAnalysed() && methodAnalysis.getSingleReturnValue() != null) {
            // if this method was identity?
            Expression srv = methodAnalysis.getSingleReturnValue();
            if (srv.isInstanceOf(InlinedMethod.class)) {
                InlinedMethod iv = srv.asInstanceOf(InlinedMethod.class);

                // special situation
                // we have an instance object, like new Pair("a", "b"), and then a getter applying to this instance object
                // this we can resolve immediately
                if (objectValue instanceof NewObject ovNo && iv.expression() instanceof VariableExpression ve) {
                    Variable variable = ve.variable();
                    if (variable instanceof FieldReference) {
                        FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                        FieldAnalysis fieldAnalysis = evaluationContext.getFieldAnalysis(fieldInfo);
                        if (fieldAnalysis.getProperty(VariableProperty.FINAL) == Level.TRUE) {
                            int i = 0;
                            List<ParameterAnalysis> parameterAnalyses = evaluationContext
                                    .getParameterAnalyses(ovNo.constructor).collect(Collectors.toList());
                            for (ParameterAnalysis parameterAnalysis : parameterAnalyses) {
                                if (parameterAnalysis.getAssignedToField() == fieldInfo) {
                                    return builder.setExpression(ovNo.getParameterExpressions().get(i)).build();
                                }
                                i++;
                            }
                        }
                    }
                }
                Map<Expression, Expression> translationMap = EvaluateParameters.translationMap(evaluationContext,
                        methodInfo, parameters);
                EvaluationResult reSrv = srv.reEvaluate(evaluationContext, translationMap);
                return builder.compose(reSrv).setExpression(reSrv.value).build();
            }
            if (srv.isConstant()) {
                return builder.setExpression(srv).build();
            }
        } else if (methodAnalysis.isBeingAnalysed()) {
            // we will, at some point, analyse this method
            return builder.setExpression(EmptyExpression.NO_VALUE).build();
        }

        // normal method value
        MethodCall methodValue = new MethodCall(objectValue, methodInfo, parameters, objectFlowOfResult);
        return builder.setExpression(methodValue).build();
    }

    private static NewObject obtainInstance(EvaluationResult.Builder builder, EvaluationContext evaluationContext, Expression objectValue) {
        if (objectValue instanceof NewObject theInstance) {
            return theInstance;
        }
        if (objectValue instanceof VariableExpression variableValue) {
            return builder.currentInstance(variableValue.variable(), ObjectFlow.NO_FLOW, EmptyExpression.EMPTY_EXPRESSION);
        }
        return null;
    }

    private static Expression valueAssistedByCompanion(EvaluationResult.Builder builder,
                                                       EvaluationContext evaluationContext,
                                                       Expression objectValue,
                                                       MethodInfo methodInfo,
                                                       MethodAnalysis methodAnalysis,
                                                       List<Expression> parameterValues) {
        NewObject instance = obtainInstance(builder, evaluationContext, objectValue);
        if (instance == null) {
            return null;
        }
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
        /*
        translationMap.put(new VariableValue(new ReturnVariable(methodInfo)),
                new MethodValue(methodInfo, new VariableValue(new This(evaluationContext.getAnalyserContext(), methodInfo.typeInfo)),
                        parameterValues, ObjectFlow.NO_FLOW));
        */
        // we might encounter isFact or isKnown, so we add the instance's state to the context
        EvaluationContext child = evaluationContext.child(instance.state, true);
        Expression resultingValue = companionValue.reEvaluate(child, translationMap).value;
        if (instance.state != EmptyExpression.EMPTY_EXPRESSION && resultingValue != EmptyExpression.EMPTY_EXPRESSION) {
            if (Primitives.isBoolean(methodInfo.returnType().typeInfo)) {
                // State is: (org.e2immu.annotatedapi.AnnotatedAPI.this.isKnown(true) and 0 == java.util.Collection.this.size())
                // Resulting value: (java.util.Set.contains(java.lang.Object) and not (0 == java.util.Collection.this.size()))
                Expression reduced = new AndExpression(evaluationContext.getPrimitives()).append(evaluationContext, instance.state, resultingValue);
                if (reduced instanceof BooleanConstant) {
                    return reduced;
                }
                if (reduced.equals(instance.state)) {
                    // only truths have been added
                    return new BooleanConstant(evaluationContext.getPrimitives(), true);
                }
            } else throw new UnsupportedOperationException("Not yet implemented");
            // unsuccessful
        }
        return null;
    }

    // IMPROVE add parameters
    private static Expression newInstanceWithTransferCompanion(EvaluationResult.Builder builder,
                                                               EvaluationContext evaluationContext,
                                                               Expression objectValue, MethodInfo methodInfo,
                                                               MethodAnalysis methodAnalysis,
                                                               List<Expression> parameterValues) {
        NewObject instance = obtainInstance(builder, evaluationContext, objectValue);
        if (instance == null) {
            return null;
        }
        Map<Expression, Expression> translationMap = new HashMap<>();
        methodAnalysis.getCompanionAnalyses().entrySet().stream()
                .filter(e -> e.getKey().action() == CompanionMethodName.Action.TRANSFER && e.getKey().aspect() != null)
                .forEach(e -> {
                    // we're assuming the aspects retain their name, but apart from the name we allow them to be different methods
                    CompanionMethodName cmn = e.getKey();
                    MethodInfo oldAspectMethod = evaluationContext.getTypeAnalysis(instance.parameterizedType.typeInfo)
                            .getAspects().get(cmn.aspect());
                    Expression oldValue = new MethodCall(
                            new VariableExpression(new This(evaluationContext.getAnalyserContext(), oldAspectMethod.typeInfo)),
                            oldAspectMethod, List.of(), ObjectFlow.NO_FLOW);
                    MethodInfo newAspectMethod = evaluationContext.getTypeAnalysis(methodInfo.typeInfo).getAspects().get(cmn.aspect());
                    Expression newValue = new MethodCall(
                            new VariableExpression(new This(evaluationContext.getAnalyserContext(), newAspectMethod.typeInfo)),
                            newAspectMethod, List.of(), ObjectFlow.NO_FLOW);
                    translationMap.put(oldValue, newValue);
                    CompanionAnalysis companionAnalysis = e.getValue();
                    ListUtil.joinLists(companionAnalysis.getParameterValues(), parameterValues)
                            .forEach(pair -> translationMap.put(pair.k, pair.v));
                });

        if (translationMap.isEmpty()) return null;
        Expression newState = instance.state.reEvaluate(evaluationContext, translationMap).value;
        // TODO object flow
        return new NewObject(null, methodInfo.returnType(), List.of(), newState, ObjectFlow.NO_FLOW);
    }

    // example 1: instance type java.util.ArrayList()[0 == java.util.ArrayList.this.size()].size()
    // this approach is independent of the companion methods: it simply searches for clauses related to the method
    // in the instance state
    private static Filter.FilterResult<MethodCall> computeEvaluationOnInstance(EvaluationResult.Builder builder,
                                                                               EvaluationContext evaluationContext,
                                                                               MethodInfo methodInfo,
                                                                               Expression objectValue,
                                                                               List<Expression> parameterValues) {
        // look for a clause that has "this.methodInfo" as a MethodValue
        NewObject instance = obtainInstance(builder, evaluationContext, objectValue);
        if (instance == null) {
            return null;
        }
        return filter(evaluationContext, methodInfo, instance.state, parameterValues);
    }

    public static Filter.FilterResult<MethodCall> filter(EvaluationContext evaluationContext,
                                                         MethodInfo methodInfo,
                                                         Expression state,
                                                         List<Expression> parameterValues) {
        List<Filter.FilterMethod<MethodCall>> filters = List.of(
                new Filter.MethodCallBooleanResult(methodInfo, parameterValues,
                        new BooleanConstant(evaluationContext.getPrimitives(), true)),
                new Filter.ValueEqualsMethodCallNoParameters(methodInfo));
        return Filter.filter(evaluationContext, state, Filter.FilterMode.ACCEPT, filters);
    }

    private static Expression computeStaticEvaluation(Primitives primitives, MethodInfo methodInfo, List<Expression> parameters) {
        if ("java.lang.Integer.toString(int)".equals(methodInfo.fullyQualifiedName()) &&
                parameters.get(0).isConstant()) {
            return new StringConstant(primitives, Integer.toString(((IntConstant) parameters.get(0)).constant()));
        }
        return null;
    }

    private static Expression computeEvaluationOnConstant(Primitives primitives, MethodInfo methodInfo, Expression objectValue) {
        if (!objectValue.isConstant()) return null;
        StringConstant stringValue;
        if ("java.lang.String.length()".equals(methodInfo.fullyQualifiedName()) &&
                (stringValue = objectValue.asInstanceOf(StringConstant.class)) != null) {
            return new IntConstant(primitives, stringValue.constant().length(), ObjectFlow.NO_FLOW);
        }
        return null;
    }

    private static Expression computeFluent(MethodAnalysis methodAnalysis, Expression scope) {
        int fluent = methodAnalysis.getProperty(VariableProperty.FLUENT);
        if (fluent == Level.DELAY && methodAnalysis.isBeingAnalysed()) return EmptyExpression.NO_VALUE;
        if (fluent != Level.TRUE) return null;
        return scope;
    }


    private static Expression computeIdentity(EvaluationContext evaluationContext,
                                              MethodAnalysis methodAnalysis,
                                              List<Expression> parameters,
                                              ObjectFlow objectFlowOfResult) {
        int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
        if (identity == Level.DELAY && methodAnalysis.isBeingAnalysed()) return EmptyExpression.NO_VALUE; // delay
        if (identity != Level.TRUE) return null;

        Map<VariableProperty, Integer> map = new HashMap<>();
        for (VariableProperty property : VariableProperty.PROPERTIES_IN_METHOD_RESULT_WRAPPER) {
            int v = methodAnalysis.getProperty(property);
            if (v != Level.DELAY) map.put(property, v);
        }
        return PropertyWrapper.propertyWrapper(evaluationContext, parameters.get(0), map, objectFlowOfResult);
    }

}