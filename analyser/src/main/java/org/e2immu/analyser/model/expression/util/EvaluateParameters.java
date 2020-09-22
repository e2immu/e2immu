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

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EvaluateParameters {

    public static Pair<EvaluationResult.Builder, List<Value>> transform(List<Expression> parameterExpressions,
                                                                        EvaluationContext evaluationContext,
                                                                        MethodInfo methodInfo,
                                                                        int notModified1Scope,
                                                                        Value scopeObject) {
        int n = methodInfo == null ? 10 : methodInfo.methodInspection.get().parameters.size();
        List<Value> parameterValues = new ArrayList<>(n);
        List<EvaluationResult> parameterResults = new ArrayList<>(n);
        int i = 0;
        int minNotNullOverParameters = MultiLevel.EFFECTIVELY_NOT_NULL;

        EvaluationResult.Builder builder = new EvaluationResult.Builder().compose(parameterResults);

        for (Expression parameterExpression : parameterExpressions) {
            Value parameterValue;
            EvaluationResult parameterResult;
            if (methodInfo != null) {
                List<ParameterInfo> params = methodInfo.methodInspection.get().parameters;
                ParameterInfo parameterInfo;
                if (i >= params.size()) {
                    ParameterInfo lastParameter = params.get(params.size() - 1);
                    if (lastParameter.parameterInspection.get().varArgs) {
                        parameterInfo = lastParameter;
                    } else {
                        throw new UnsupportedOperationException("?");
                    }
                } else {
                    parameterInfo = params.get(i);
                }
                // NOT_NULL, NOT_MODIFIED, SIZE
                Map<VariableProperty, Integer> map = parameterInfo.parameterAnalysis.get().getProperties(VariableProperty.FORWARD_PROPERTIES_ON_PARAMETERS);

                if (notModified1Scope == Level.TRUE) {
                    map.put(VariableProperty.MODIFIED, Level.FALSE);
                }

                if (map.containsValue(Level.DELAY)) {
                    map.put(VariableProperty.METHOD_DELAY, Level.TRUE);
                }
                if (notModified1Scope != Level.TRUE && methodInfo.isSingleAbstractMethod()) {
                    // we compute on the parameter expression, not the value (chicken and egg)
                    Boolean cannotBeModified = parameterExpression.returnType().isImplicitlyOrAtLeastEventuallyE2Immutable(methodInfo.typeInfo);
                    if (cannotBeModified == null) {
                        map.put(VariableProperty.METHOD_DELAY, Level.TRUE); // DELAY
                    } else if (cannotBeModified) {
                        map.put(VariableProperty.MODIFIED, Level.FALSE);
                    }
                }

                if (parameterInfo.parameterizedType.isFunctionalInterface()) {
                    Boolean undeclared = tryToDetectUndeclared(evaluationContext, parameterExpression);
                    if (undeclared == Boolean.TRUE &&
                            evaluationContext.getCurrentMethod() != null &&
                            !evaluationContext.getCurrentMethod().methodAnalysis.copyModificationStatusFrom.isSet(methodInfo)) {
                        evaluationContext.getCurrentMethod().methodAnalysis.copyModificationStatusFrom.put(methodInfo, true);
                    }
                }
                int notNull = map.getOrDefault(VariableProperty.NOT_NULL, Level.DELAY);
                minNotNullOverParameters = Math.min(minNotNullOverParameters, notNull);

                ForwardEvaluationInfo forward = new ForwardEvaluationInfo(map, true);
                parameterResult = parameterExpression.evaluate(evaluationContext, forward);
                parameterResults.add(parameterResult);
                parameterValue = parameterResult.value;

                ObjectFlow source = parameterValue.getObjectFlow();
                int modified = map.getOrDefault(VariableProperty.MODIFIED, Level.DELAY);
                if (modified == Level.DELAY) {
                    source.delay();
                } else {
                    ObjectFlow destination = parameterInfo.parameterAnalysis.get().getObjectFlow();
                    builder.addCallOut(modified == Level.TRUE, destination, parameterValue);
                }
            } else {
                parameterResult = parameterExpression.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
                parameterValue = parameterResult.value;
                parameterResults.add(parameterResult);
            }

            builder.compose(parameterResult);
            parameterValues.add(parameterValue);
            i++;
        }

        ValueWithVariable scopeVariable;
        if (minNotNullOverParameters == MultiLevel.EFFECTIVELY_NOT_NULL &&
                i > 0 &&
                methodInfo != null &&
                scopeObject != null &&
                methodInfo.typeInfo.isFunctionalInterface() &&
                (scopeVariable = scopeObject.asInstanceOf(ValueWithVariable.class)) != null) {
            builder.addPropertyRestriction(scopeVariable.variable, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL);
        }

        if (methodInfo != null && methodInfo.methodAnalysis.isSet() && methodInfo.methodAnalysis.get().precondition.isSet()) {
            // there is a precondition, and we have a list of values... let's see what we can learn
            // the precondition is using parameter info's as variables so we'll have to substitute
            Value precondition = methodInfo.methodAnalysis.get().precondition.get();
            Map<Value, Value> translationMap = translationMap(evaluationContext, methodInfo, parameterValues);
            Value reEvaluated = precondition.reEvaluate(evaluationContext, translationMap);
            // from the result we either may infer another condition, or values to be set...

            // NOT_NULL
            Map<Variable, Value> individualNullClauses = reEvaluated.filter(Value.FilterMode.ACCEPT, Value::isIndividualNotNullClause).accepted;
            for (Map.Entry<Variable, Value> nullClauseEntry : individualNullClauses.entrySet()) {
                if (!(nullClauseEntry.getValue().isInstanceOf(NullValue.class)) && nullClauseEntry.getKey() instanceof ParameterInfo) {
                    builder.addPropertyRestriction(nullClauseEntry.getKey(), VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
                }
            }

            // SIZE
            Map<Variable, Value> sizeRestrictions = reEvaluated.filter(Value.FilterMode.ACCEPT, Value::isIndividualSizeRestriction).accepted;
            for (Map.Entry<Variable, Value> sizeRestriction : sizeRestrictions.entrySet()) {
                // now back to precondition world
                if (sizeRestriction.getKey() instanceof ParameterInfo) {
                    int v = sizeRestriction.getValue().encodedSizeRestriction();
                    if (v > Level.NOT_A_SIZE) {
                        builder.addPropertyRestriction(sizeRestriction.getKey(), VariableProperty.SIZE, v);
                    }
                }
            }

            // all the rest: preconditions
            // TODO: also weed out conditions that are not on parameters, and not on `this`
            Value rest = reEvaluated.filter(Value.FilterMode.ACCEPT, Value::isIndividualNotNullClauseOnParameter, Value::isIndividualSizeRestrictionOnParameter).rest;
            if (rest != null) {
                builder.addPrecondition(rest);
            }
        }
        return new Pair<>(builder, parameterValues);
    }

    public static Map<Value, Value> translationMap(EvaluationContext evaluationContext, MethodInfo methodInfo, List<Value> parameters) {
        ImmutableMap.Builder<Value, Value> builder = new ImmutableMap.Builder<>();
        int i = 0;
        for (Value parameterValue : parameters) {
            ParameterInfo parameterInfo = methodInfo.methodInspection.get().parameters.get(i);
            Value vv = new VariableValue(evaluationContext, parameterInfo, parameterInfo.name);
            builder.put(vv, parameterValue);
            i++;
        }
        return builder.build();
    }


    // we should normally look at the value, but there is a chicken and egg problem
    public static Boolean tryToDetectUndeclared(EvaluationContext evaluationContext, Expression scope) {
        if (scope instanceof VariableExpression) {
            VariableExpression variableExpression = (VariableExpression) scope;
            if (variableExpression.variable instanceof ParameterInfo) return true;
            Value value = evaluationContext.currentValue(variableExpression.variable);
            if (value == UnknownValue.NO_VALUE) return null; // delay
            // TODO
            return true;
        }
        return false;
    }
}
