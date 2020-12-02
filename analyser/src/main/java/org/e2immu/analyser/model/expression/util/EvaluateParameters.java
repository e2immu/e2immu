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
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.Filter;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.model.value.VariableValue;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.Logger;
import org.e2immu.analyser.util.Pair;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;

public class EvaluateParameters {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(EvaluateParameters.class);

    public static Pair<EvaluationResult.Builder, List<Value>> transform(List<Expression> parameterExpressions,
                                                                        EvaluationContext evaluationContext,
                                                                        MethodInfo methodInfo,
                                                                        int notModified1Scope,
                                                                        Value scopeObject) {
        int n = methodInfo == null ? 10 : methodInfo.methodInspection.get().getParameters().size();
        List<Value> parameterValues = new ArrayList<>(n);
        List<EvaluationResult> parameterResults = new ArrayList<>(n);
        int i = 0;
        int minNotNullOverParameters = MultiLevel.EFFECTIVELY_NOT_NULL;

        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(parameterResults);

        for (Expression parameterExpression : parameterExpressions) {
            Value parameterValue;
            EvaluationResult parameterResult;
            if (methodInfo != null) {
                List<ParameterInfo> params = methodInfo.methodInspection.get().getParameters();
                ParameterInfo parameterInfo;
                if (i >= params.size()) {
                    ParameterInfo lastParameter = params.get(params.size() - 1);
                    if (lastParameter.parameterInspection.get().isVarArgs()) {
                        parameterInfo = lastParameter;
                    } else {
                        throw new UnsupportedOperationException("?");
                    }
                } else {
                    parameterInfo = params.get(i);
                }
                // NOT_NULL, NOT_MODIFIED, SIZE
                Map<VariableProperty, Integer> map;
                try {
                  map = evaluationContext.getParameterAnalysis(parameterInfo)
                            .getProperties(VariableProperty.FORWARD_PROPERTIES_ON_PARAMETERS);

                } catch (RuntimeException e) {
                    LOGGER.error("Failed to obtain parameter analysis of {}", parameterInfo.fullyQualifiedName());
                    throw e;
                }
                if (notModified1Scope == Level.TRUE) {
                    map.put(VariableProperty.MODIFIED, Level.FALSE);
                }

                if (map.containsValue(Level.DELAY)) {
                    map.put(VariableProperty.METHOD_DELAY, Level.TRUE);
                }
                if (notModified1Scope != Level.TRUE && methodInfo.isSingleAbstractMethod()) {
                    // we compute on the parameter expression, not the value (chicken and egg)
                    TypeAnalysis typeAnalysis = evaluationContext.getTypeAnalysis(methodInfo.typeInfo);
                    Boolean cannotBeModified = parameterExpression.returnType()
                            .isImplicitlyOrAtLeastEventuallyE2Immutable(evaluationContext.getAnalyserContext());
                    if (cannotBeModified == null) {
                        map.put(VariableProperty.METHOD_DELAY, Level.TRUE); // DELAY
                    } else if (cannotBeModified) {
                        map.put(VariableProperty.MODIFIED, Level.FALSE);
                    }
                }

                if (parameterInfo.parameterizedType.isFunctionalInterface()) {
                    Boolean undeclared = tryToDetectUndeclared(evaluationContext, parameterExpression);
                    MethodLevelData methodLevelData = evaluationContext.getCurrentMethod() != null ?
                            evaluationContext.getCurrentMethod().methodLevelData() : null;
                    if (undeclared == Boolean.TRUE && methodLevelData != null &&
                            !methodLevelData.copyModificationStatusFrom.isSet(methodInfo)) {
                        methodLevelData.copyModificationStatusFrom.put(methodInfo, true);
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
                    Logger.log(DELAYED, "Delaying flow access registration because modification status of {} not known",
                            methodInfo.fullyQualifiedName());
                    source.delay();
                } else {
                    ParameterAnalysis parameterAnalysis = evaluationContext.getParameterAnalysis(parameterInfo);
                    ObjectFlow destination = parameterAnalysis.getObjectFlow();
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

        VariableValue scopeVariable;
        if (minNotNullOverParameters == MultiLevel.EFFECTIVELY_NOT_NULL &&
                i > 0 &&
                methodInfo != null &&
                scopeObject != null &&
                methodInfo.typeInfo.typeInspection.get().isFunctionalInterface() &&
                (scopeVariable = scopeObject.asInstanceOf(VariableValue.class)) != null) {
            builder.setProperty(scopeVariable.variable, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL);
        }


        if (methodInfo != null) {
            MethodAnalysis methodAnalysis = evaluationContext.getMethodAnalysis(methodInfo);
            Value precondition = methodAnalysis.getPrecondition();
            if (precondition != null && precondition != EmptyExpression.EMPTY_EXPRESSION) {
                // there is a precondition, and we have a list of values... let's see what we can learn
                // the precondition is using parameter info's as variables so we'll have to substitute
                Map<Value, Value> translationMap = translationMap(evaluationContext, methodInfo, parameterValues);
                EvaluationResult eRreEvaluated = precondition.reEvaluate(evaluationContext, translationMap);
                Value reEvaluated = eRreEvaluated.value;
                builder.compose(eRreEvaluated);

                // from the result we either may infer another condition, or values to be set...

                // NOT_NULL
                Map<ParameterInfo, Value> individualNullClauses = Filter.filter(evaluationContext, reEvaluated,
                        Filter.FilterMode.ACCEPT,
                        Filter.INDIVIDUAL_NULL_OR_NOT_NULL_CLAUSE_ON_PARAMETER).accepted();
                for (Map.Entry<ParameterInfo, Value> nullClauseEntry : individualNullClauses.entrySet()) {
                    if (nullClauseEntry.getValue() != NullValue.NULL_VALUE) {
                        builder.setProperty(nullClauseEntry.getKey(), VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
                    }
                }

                // all the rest: preconditions
                // TODO: also weed out conditions that are not on parameters, and not on `this`
                Value rest = Filter.filter(evaluationContext, reEvaluated,
                        Filter.FilterMode.ACCEPT, Filter.INDIVIDUAL_NULL_OR_NOT_NULL_CLAUSE_ON_PARAMETER).rest();
                if (rest != null) {
                    builder.addPrecondition(rest);
                }
            }
        }
        return new Pair<>(builder, parameterValues);
    }

    public static Map<Value, Value> translationMap(EvaluationContext evaluationContext, MethodInfo methodInfo, List<Value> parameters) {
        ImmutableMap.Builder<Value, Value> builder = new ImmutableMap.Builder<>();
        int i = 0;
        for (Value parameterValue : parameters) {
            ParameterInfo parameterInfo = methodInfo.methodInspection.get().getParameters().get(i);
            Value vv = new VariableValue(parameterInfo, parameterValue.getObjectFlow());
            builder.put(vv, parameterValue);
            i++;
        }
        return builder.build();
    }


    // we should normally look at the value, but there is a chicken and egg problem
    public static Boolean tryToDetectUndeclared(EvaluationContext evaluationContext, Expression scope) {
        if (scope instanceof VariableExpression variableExpression) {
            if (variableExpression.variable instanceof ParameterInfo) return true;
            Value value = evaluationContext.currentValue(variableExpression.variable);
            if (value == EmptyExpression.NO_VALUE) return null; // delay
            // TODO
            return true;
        }
        return false;
    }
}
