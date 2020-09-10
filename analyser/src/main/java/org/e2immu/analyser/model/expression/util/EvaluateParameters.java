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
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.ValueWithVariable;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.ObjectFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.e2immu.analyser.util.Logger.LogTarget.NOT_MODIFIED;
import static org.e2immu.analyser.util.Logger.LogTarget.NOT_NULL;
import static org.e2immu.analyser.util.Logger.log;

public class EvaluateParameters {

    public static List<Value> transform(List<Expression> parameterExpressions,
                                        EvaluationContext evaluationContext,
                                        EvaluationVisitor visitor,
                                        MethodInfo methodInfo,
                                        int notModified1Scope,
                                        Value scopeObject) {
        List<Value> parameterValues = new ArrayList<>();
        int i = 0;
        int minNotNullOverParameters = MultiLevel.EFFECTIVELY_NOT_NULL;
        for (Expression parameterExpression : parameterExpressions) {
            Value parameterValue;
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
                if (map.containsValue(Level.DELAY)) {
                    map.put(VariableProperty.METHOD_DELAY, Level.TRUE);
                }
                if (notModified1Scope == Level.TRUE) {
                    map.put(VariableProperty.MODIFIED, 0);
                }
                int notNull = map.getOrDefault(VariableProperty.NOT_NULL, Level.DELAY);
                minNotNullOverParameters = Math.min(minNotNullOverParameters, notNull);

                ForwardEvaluationInfo forward = new ForwardEvaluationInfo(map, true);
                parameterValue = parameterExpression.evaluate(evaluationContext, visitor, forward);

                if (methodInfo.isSingleAbstractMethod()) {
                    handleSAM(methodInfo, parameterInfo.parameterizedType, parameterValue, notModified1Scope);
                }

                ObjectFlow source = parameterValue.getObjectFlow();
                int modified = map.getOrDefault(VariableProperty.MODIFIED, Level.DELAY);
                if (modified == Level.DELAY) {
                    source.delay();
                } else {
                    ObjectFlow destination = parameterInfo.parameterAnalysis.get().getObjectFlow();
                    evaluationContext.addCallOut(modified == Level.TRUE, destination, parameterValue);
                }
            } else {
                parameterValue = parameterExpression.evaluate(evaluationContext, visitor, ForwardEvaluationInfo.DEFAULT);
            }

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
            evaluationContext.addPropertyRestriction(scopeVariable.variable, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL);
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
                    evaluationContext.addPropertyRestriction(nullClauseEntry.getKey(), VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
                }
            }

            // SIZE
            Map<Variable, Value> sizeRestrictions = reEvaluated.filter(Value.FilterMode.ACCEPT, Value::isIndividualSizeRestriction).accepted;
            for (Map.Entry<Variable, Value> sizeRestriction : sizeRestrictions.entrySet()) {
                // now back to precondition world
                if (sizeRestriction.getKey() instanceof ParameterInfo) {
                    int v = sizeRestriction.getValue().encodedSizeRestriction();
                    if (v > Level.NOT_A_SIZE) {
                        evaluationContext.addPropertyRestriction(sizeRestriction.getKey(), VariableProperty.SIZE, v);
                    }
                }
            }

            // all the rest: preconditions
            // TODO: also weed out conditions that are not on parameters, and not on `this`
            Value rest = reEvaluated.filter(Value.FilterMode.ACCEPT, Value::isIndividualNotNullClause, Value::isIndividualSizeRestriction).rest;
            if (rest != null) {
                evaluationContext.addPrecondition(rest);
            }
        }
        return parameterValues;
    }

    private static void handleSAM(MethodInfo methodInfo, ParameterizedType formalParameterType, Value parameterValue, int notModified1) {
        if (notModified1 == Level.TRUE) {
            // we're in the non-modifying situation
            log(NOT_MODIFIED, "In the @NM1 situation with " + methodInfo.name + " and " + parameterValue);
        } else {
            Boolean cannotBeModified = formalParameterType.isImplicitlyOrAtLeastEventuallyE2Immutable(methodInfo.typeInfo);
            if (cannotBeModified == null) return; // DELAY
            if (cannotBeModified) {
                // we're in the @Exposed situation
                log(NOT_MODIFIED, "In the @Exposed situation with " + methodInfo.name + " and " + parameterValue);

            } else {
                log(NOT_MODIFIED, "In the @Modified situation with " + methodInfo.name + " and " + parameterValue);
            }
        }
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
}
