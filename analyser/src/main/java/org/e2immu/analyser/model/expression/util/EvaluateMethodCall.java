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
import org.e2immu.analyser.model.value.*;
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
                                               Value objectValue,
                                               List<Value> parameters,
                                               ObjectFlow objectFlowOfResult) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

        Objects.requireNonNull(evaluationContext);

        // no value (method call on field that does not have effective value yet)
        if (objectValue == UnknownValue.NO_VALUE) {
            return builder.setValue(UnknownValue.NO_VALUE).build(); // this will delay
        }

        if (ShallowTypeAnalyser.IS_KNOWN_FQN.equals(methodInfo.fullyQualifiedName) &&
                !evaluationContext.getAnalyserContext().inAnnotatedAPIAnalysis() &&
                parameters.get(0) instanceof BoolValue boolValue) {
            Value object = new VariableValue(new This(evaluationContext.getAnalyserContext(), methodInfo.typeInfo));
            Value clause = new MethodValue(methodInfo, object, List.of(BoolValue.createTrue(evaluationContext.getPrimitives())), objectFlowOfResult);
            if (boolValue.value) {
                // isKnown(true) -> return BoolValue.TRUE or BoolValue.FALSE, depending on state
                Filter.FilterResult<Value> res = Filter.filter(evaluationContext, evaluationContext.getConditionManager().state,
                        Filter.FilterMode.ACCEPT, new Filter.ExactValue(clause));
                boolean isKnown = !res.accepted().isEmpty();
                Value result = BoolValue.create(evaluationContext.getPrimitives(), isKnown);
                return builder.setValue(result).build();
            } else {
                // isKnown(false)-> return MethodValue
                return builder.setValue(clause).build();
            }
        }

        // static eval: Integer.toString(3)
        Value knownStaticEvaluation = computeStaticEvaluation(evaluationContext.getPrimitives(), methodInfo, parameters);
        if (knownStaticEvaluation != null) {
            return builder.setValue(knownStaticEvaluation).build();
        }

        // eval on constant, like "abc".length()
        Value evaluationOnConstant = computeEvaluationOnConstant(evaluationContext.getPrimitives(),
                methodInfo, objectValue);
        if (evaluationOnConstant != null) {
            return builder.setValue(evaluationOnConstant).build();
        }

        if (!evaluationContext.getAnalyserContext().inAnnotatedAPIAnalysis()) {
            // boolean added = set.add(e);  -- if the set is empty, we know the result will be "true"
            Value assistedByCompanion = valueAssistedByCompanion(builder, evaluationContext,
                    objectValue, methodInfo, methodAnalysis, parameters);
            if (assistedByCompanion != null) {
                return builder.setValue(assistedByCompanion).build();
            }

            if (modified == Level.FALSE) {

                // new object returned, with a transfer of the aspect; 5 == stringBuilder.length() in aspect -> 5 == stringBuilder.toString().length()
                Value newInstance = newInstanceWithTransferCompanion(builder, evaluationContext, objectValue, methodInfo, methodAnalysis, parameters);
                if (newInstance != null) {
                    return builder.setValue(newInstance).build();
                }

                // evaluation on Instance, with state; check companion methods
                // TYPE 1: boolean expression of aspect; e.g., xx == aspect method (5 == string.length())
                // TYPE 2: boolean clause; e.g., contains("a")
                Filter.FilterResult<MethodValue> evaluationOnInstance =
                        computeEvaluationOnInstance(builder, evaluationContext, methodInfo, objectValue, parameters);
                if (evaluationOnInstance != null && !evaluationOnInstance.accepted().isEmpty()) {
                    Value value = evaluationOnInstance.accepted().values().stream().findFirst().orElseThrow();
                    return builder.setValue(value).build();
                }
            }
        }

        // @Identity as method annotation
        Value identity = computeIdentity(evaluationContext, methodAnalysis, parameters, objectFlowOfResult);
        if (identity != null) {
            return builder.setValue(identity).build();
        }

        // @Fluent as method annotation
        // fluent methods are modifying
        Value fluent = computeFluent(methodAnalysis, objectValue);
        if (fluent != null) {
            return builder.setValue(fluent).build();
        }

        InlineValue inlineValue;
        if (methodInfo.typeInfo.typeInspection.get().isFunctionalInterface() &&
                (inlineValue = objectValue.asInstanceOf(InlineValue.class)) != null &&
                inlineValue.canBeApplied(evaluationContext)) {
            Map<Value, Value> translationMap = EvaluateParameters.translationMap(evaluationContext, methodInfo, parameters);
            EvaluationResult reInline = inlineValue.reEvaluate(evaluationContext, translationMap);
            return builder.compose(reInline).setValue(reInline.value).build();
        }

        // singleReturnValue implies non-modifying
        if (methodAnalysis.isBeingAnalysed() && methodAnalysis.getSingleReturnValue() != null) {
            // if this method was identity?
            Value srv = methodAnalysis.getSingleReturnValue();
            if (srv.isInstanceOf(InlineValue.class)) {
                InlineValue iv = srv.asInstanceOf(InlineValue.class);

                // special situation
                // we have an instance object, like new Pair("a", "b"), and then a getter applying to this instance object
                // this we can resolve immediately
                if (objectValue instanceof Instance && iv.value instanceof VariableValue) {
                    Variable variable = ((VariableValue) iv.value).variable;
                    if (variable instanceof FieldReference) {
                        FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                        FieldAnalysis fieldAnalysis = evaluationContext.getFieldAnalysis(fieldInfo);
                        if (fieldAnalysis.getProperty(VariableProperty.FINAL) == Level.TRUE) {
                            Instance instance = (Instance) objectValue;
                            int i = 0;
                            List<ParameterAnalysis> parameterAnalyses = evaluationContext
                                    .getParameterAnalyses(instance.constructor).collect(Collectors.toList());
                            for (ParameterAnalysis parameterAnalysis : parameterAnalyses) {
                                if (parameterAnalysis.getAssignedToField() == fieldInfo) {
                                    return builder.setValue(instance.constructorParameterValues.get(i)).build();
                                }
                                i++;
                            }
                        }
                    }
                }
                Map<Value, Value> translationMap = EvaluateParameters.translationMap(evaluationContext,
                        methodInfo, parameters);
                EvaluationResult reSrv = srv.reEvaluate(evaluationContext, translationMap);
                return builder.compose(reSrv).setValue(reSrv.value).build();
            }
            if (srv.isConstant()) {
                return builder.setValue(srv).build();
            }
        } else if (methodAnalysis.isBeingAnalysed()) {
            // we will, at some point, analyse this method
            return builder.setValue(UnknownValue.NO_VALUE).build();
        }

        // normal method value
        MethodValue methodValue = new MethodValue(methodInfo, objectValue, parameters, objectFlowOfResult);
        return builder.setValue(methodValue).build();
    }

    private static Instance obtainInstance(EvaluationResult.Builder builder, EvaluationContext evaluationContext, Value objectValue) {
        if (objectValue instanceof Instance theInstance) {
            return theInstance;
        }
        if (objectValue instanceof VariableValue variableValue) {
            return builder.currentInstance(variableValue.variable, ObjectFlow.NO_FLOW, UnknownValue.EMPTY);
        }
        return null;
    }

    private static Value valueAssistedByCompanion(EvaluationResult.Builder builder,
                                                  EvaluationContext evaluationContext,
                                                  Value objectValue,
                                                  MethodInfo methodInfo,
                                                  MethodAnalysis methodAnalysis,
                                                  List<Value> parameterValues) {
        Instance instance = obtainInstance(builder, evaluationContext, objectValue);
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
        Value companionValue = companionAnalysis.getValue();
        Map<Value, Value> translationMap = new HashMap<>();

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
        EvaluationContext child = evaluationContext.child(instance.state);
        Value resultingValue = companionValue.reEvaluate(child, translationMap).value;
        if (instance.state != UnknownValue.EMPTY && resultingValue != UnknownValue.EMPTY) {
            if (Primitives.isBoolean(methodInfo.returnType().typeInfo)) {
                // State is: (org.e2immu.annotatedapi.AnnotatedAPI.this.isKnown(true) and 0 == java.util.Collection.this.size())
                // Resulting value: (java.util.Set.contains(java.lang.Object) and not (0 == java.util.Collection.this.size()))
                Value reduced = new AndValue(evaluationContext.getPrimitives()).append(evaluationContext, instance.state, resultingValue);
                if (reduced instanceof BoolValue) {
                    return reduced;
                }
                if (reduced.equals(instance.state)) {
                    // only truths have been added
                    return BoolValue.createTrue(evaluationContext.getPrimitives());
                }
            } else throw new UnsupportedOperationException("Not yet implemented");
            // unsuccessful
        }
        return null;
    }

    // IMPROVE add parameters
    private static Value newInstanceWithTransferCompanion(EvaluationResult.Builder builder,
                                                          EvaluationContext evaluationContext,
                                                          Value objectValue, MethodInfo methodInfo,
                                                          MethodAnalysis methodAnalysis,
                                                          List<Value> parameterValues) {
        Instance instance = obtainInstance(builder, evaluationContext, objectValue);
        if (instance == null) {
            return null;
        }
        Map<Value, Value> translationMap = new HashMap<>();
        methodAnalysis.getCompanionAnalyses().entrySet().stream()
                .filter(e -> e.getKey().action() == CompanionMethodName.Action.TRANSFER && e.getKey().aspect() != null)
                .forEach(e -> {
                    // we're assuming the aspects retain their name, but apart from the name we allow them to be different methods
                    CompanionMethodName cmn = e.getKey();
                    MethodInfo oldAspectMethod = evaluationContext.getTypeAnalysis(instance.parameterizedType.typeInfo)
                            .getAspects().get(cmn.aspect());
                    Value oldValue = new MethodValue(oldAspectMethod,
                            new VariableValue(new This(evaluationContext.getAnalyserContext(), oldAspectMethod.typeInfo)),
                            List.of(), ObjectFlow.NO_FLOW);
                    MethodInfo newAspectMethod = evaluationContext.getTypeAnalysis(methodInfo.typeInfo).getAspects().get(cmn.aspect());
                    Value newValue = new MethodValue(newAspectMethod,
                            new VariableValue(new This(evaluationContext.getAnalyserContext(), newAspectMethod.typeInfo)),
                            List.of(), ObjectFlow.NO_FLOW);
                    translationMap.put(oldValue, newValue);
                    CompanionAnalysis companionAnalysis = e.getValue();
                    ListUtil.joinLists(companionAnalysis.getParameterValues(), parameterValues)
                            .forEach(pair -> translationMap.put(pair.k, pair.v));
                });

        if (translationMap.isEmpty()) return null;
        Value newState = instance.state.reEvaluate(evaluationContext, translationMap).value;
        // TODO object flow
        return new Instance(methodInfo.returnType(), null, List.of(), ObjectFlow.NO_FLOW, newState);
    }

    // example 1: instance type java.util.ArrayList()[0 == java.util.ArrayList.this.size()].size()
    // this approach is independent of the companion methods: it simply searches for clauses related to the method
    // in the instance state
    private static Filter.FilterResult<MethodValue> computeEvaluationOnInstance(EvaluationResult.Builder builder,
                                                                                EvaluationContext evaluationContext,
                                                                                MethodInfo methodInfo,
                                                                                Value objectValue,
                                                                                List<Value> parameterValues) {
        // look for a clause that has "this.methodInfo" as a MethodValue
        Instance instance = obtainInstance(builder, evaluationContext, objectValue);
        if (instance == null) {
            return null;
        }
        return filter(evaluationContext, methodInfo, instance.state, parameterValues);
    }

    public static Filter.FilterResult<MethodValue> filter(EvaluationContext evaluationContext,
                                                          MethodInfo methodInfo,
                                                          Value state,
                                                          List<Value> parameterValues) {
        List<Filter.FilterMethod<MethodValue>> filters = List.of(
                new Filter.MethodCallBooleanResult(methodInfo, parameterValues,
                        BoolValue.createTrue(evaluationContext.getPrimitives())),
                new Filter.ValueEqualsMethodCallNoParameters(methodInfo));
        return Filter.filter(evaluationContext, state, Filter.FilterMode.ACCEPT, filters);
    }

    private static Value computeStaticEvaluation(Primitives primitives, MethodInfo methodInfo, List<Value> parameters) {
        if ("java.lang.Integer.toString(int)".equals(methodInfo.fullyQualifiedName()) &&
                parameters.get(0).isConstant()) {
            return new StringValue(primitives, Integer.toString(((IntValue) parameters.get(0)).getValue()));
        }
        return null;
    }

    private static Value computeEvaluationOnConstant(Primitives primitives, MethodInfo methodInfo, Value objectValue) {
        if (!objectValue.isConstant()) return null;
        StringValue stringValue;
        if ("java.lang.String.length()".equals(methodInfo.fullyQualifiedName()) &&
                (stringValue = objectValue.asInstanceOf(StringValue.class)) != null) {
            return new IntValue(primitives, stringValue.value.length(), ObjectFlow.NO_FLOW);
        }
        return null;
    }

    private static Value computeFluent(MethodAnalysis methodAnalysis, Value scope) {
        int fluent = methodAnalysis.getProperty(VariableProperty.FLUENT);
        if (fluent == Level.DELAY && methodAnalysis.isBeingAnalysed()) return UnknownValue.NO_VALUE;
        if (fluent != Level.TRUE) return null;
        return scope;
    }


    private static Value computeIdentity(EvaluationContext evaluationContext,
                                         MethodAnalysis methodAnalysis,
                                         List<Value> parameters,
                                         ObjectFlow objectFlowOfResult) {
        int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
        if (identity == Level.DELAY && methodAnalysis.isBeingAnalysed()) return UnknownValue.NO_VALUE; // delay
        if (identity != Level.TRUE) return null;

        Map<VariableProperty, Integer> map = new HashMap<>();
        for (VariableProperty property : VariableProperty.PROPERTIES_IN_METHOD_RESULT_WRAPPER) {
            int v = methodAnalysis.getProperty(property);
            if (v != Level.DELAY) map.put(property, v);
        }
        return PropertyWrapper.propertyWrapper(evaluationContext, parameters.get(0), map, objectFlowOfResult);
    }

}
