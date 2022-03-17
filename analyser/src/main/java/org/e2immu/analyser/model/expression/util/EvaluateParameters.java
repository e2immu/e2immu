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
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.util.Pair;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvaluateParameters {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(EvaluateParameters.class);
    private static final Map<Property, DV> RECURSIVE_CALL =
            Map.of(Property.CONTEXT_MODIFIED, DV.FALSE_DV,
                    Property.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV);

    public static Pair<EvaluationResult.Builder, List<Expression>> transform(List<Expression> parameterExpressions,
                                                                             EvaluationResult context,
                                                                             ForwardEvaluationInfo forwardEvaluationInfo,
                                                                             MethodInfo methodInfo,
                                                                             boolean recursiveOrPartOfCallCycle,
                                                                             Expression scopeObject) {
        int n = methodInfo == null ? 10 : methodInfo.methodInspection.get().getParameters().size();
        List<Expression> parameterValues = new ArrayList<>(n);
        int i = 0;
        DV minCnnOverParameters = MultiLevel.EFFECTIVELY_NOT_NULL_DV;

        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);

        DV scopeIsContainer = scopeObject == null || recursiveOrPartOfCallCycle ? MultiLevel.NOT_CONTAINER_DV
                : context.evaluationContext().getProperty(scopeObject, Property.CONTAINER, true, true);

        for (Expression parameterExpression : parameterExpressions) {
            minCnnOverParameters = oneParameterReturnCnn(context, forwardEvaluationInfo,
                    methodInfo, recursiveOrPartOfCallCycle, parameterValues, i, builder, parameterExpression,
                    scopeIsContainer);
            i++;
        }

        VariableExpression scopeVariable;
        if (minCnnOverParameters.equals(MultiLevel.EFFECTIVELY_NOT_NULL_DV) &&
                i > 0 &&
                methodInfo != null &&
                scopeObject != null &&
                methodInfo.typeInfo.typeInspection.get().isFunctionalInterface() &&
                (scopeVariable = scopeObject.asInstanceOf(VariableExpression.class)) != null) {
            builder.setProperty(scopeVariable.variable(), Property.CONTEXT_NOT_NULL,
                    MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV);
        }
        return new Pair<>(builder, parameterValues);
    }

    private static DV oneParameterReturnCnn(EvaluationResult context,
                                            ForwardEvaluationInfo forwardEvaluationInfo,
                                            MethodInfo methodInfo,
                                            boolean recursiveOrPartOfCallCycle,
                                            List<Expression> parameterValues,
                                            int position,
                                            EvaluationResult.Builder builder,
                                            Expression parameterExpression,
                                            DV scopeIsContainer) {
        Expression parameterValue;
        EvaluationResult parameterResult;
        DV contextNotNull;
        if (methodInfo != null) {
            ParameterInfo parameterInfo = getParameterInfo(methodInfo, position);
            // NOT_NULL, NOT_MODIFIED
            Map<Property, DV> map;
            try {
                MethodAnalyser currentMethod = context.getCurrentMethod();
                if (currentMethod != null &&
                        currentMethod.getMethodInfo() == methodInfo) {
                    map = new HashMap<>(RECURSIVE_CALL);
                } else {
                    // copy from parameter into map used for forwarding
                    ParameterAnalysis parameterAnalysis = context.getAnalyserContext().getParameterAnalysis(parameterInfo);
                    map = new HashMap<>();
                    map.put(Property.CONTEXT_MODIFIED, parameterAnalysis.getProperty(Property.MODIFIED_VARIABLE));
                    map.put(Property.CONTEXT_NOT_NULL, parameterAnalysis.getProperty(Property.NOT_NULL_PARAMETER));
                    map.put(Property.CONTEXT_CONTAINER, parameterAnalysis.getProperty(Property.CONTAINER));
                }
            } catch (RuntimeException e) {
                LOGGER.error("Failed to obtain parameter analysis of {}", parameterInfo.fullyQualifiedName());
                throw e;
            }

            doContextContainer(methodInfo, recursiveOrPartOfCallCycle, map);
            doContextModified(methodInfo, recursiveOrPartOfCallCycle, map, scopeIsContainer);
            contextNotNull = map.getOrDefault(Property.CONTEXT_NOT_NULL, null);
            if (contextNotNull.isDelayed() && recursiveOrPartOfCallCycle) {
                map.put(Property.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV); // won't be me to rock the boat
            }

            ForwardEvaluationInfo forward = new ForwardEvaluationInfo(map, forwardEvaluationInfo.doNotReevaluateVariableExpressions(), true,
                    forwardEvaluationInfo.assignmentTarget(), true, forwardEvaluationInfo.stage());
            parameterResult = parameterExpression.evaluate(context, forward);
            parameterValue = parameterResult.value();
        } else {
            parameterResult = parameterExpression.evaluate(context, ForwardEvaluationInfo.DEFAULT);
            parameterValue = parameterResult.value();
            contextNotNull = Property.CONTEXT_NOT_NULL.bestDv;
        }

        builder.compose(parameterResult);
        parameterValues.add(parameterValue);
        return contextNotNull;
    }

    private static void doContextContainer(MethodInfo methodInfo,
                                           boolean recursiveOrPartOfCallCycle,
                                           Map<Property, DV> map) {
        if (recursiveOrPartOfCallCycle) {
            map.put(Property.CONTEXT_CONTAINER, MultiLevel.NOT_CONTAINER_DV);
        } else {
            DV contextContainer = map.getOrDefault(Property.CONTEXT_CONTAINER, null);
            if (contextContainer == null) {
                CausesOfDelay delay = methodInfo.delay(CauseOfDelay.Cause.CONTEXT_CONTAINER);
                map.put(Property.CONTEXT_CONTAINER, delay);
            }
        }
    }

    private static void doContextModified(MethodInfo methodInfo,
                                          boolean recursiveOrPartOfCallCycle,
                                          Map<Property, DV> map,
                                          DV scopeIsContainer) {
        if (recursiveOrPartOfCallCycle || scopeIsContainer.equals(MultiLevel.CONTAINER_DV)) {
            map.put(Property.CONTEXT_MODIFIED, DV.FALSE_DV);
        } else if (scopeIsContainer.isDelayed()) {
            map.merge(Property.CONTEXT_MODIFIED, scopeIsContainer, DV::maxIgnoreDelay);
        } else {
            DV contextModified = map.getOrDefault(Property.CONTEXT_MODIFIED, null);
            if (contextModified == null) {
                CausesOfDelay delay = methodInfo.delay(CauseOfDelay.Cause.CONTEXT_MODIFIED);
                map.put(Property.CONTEXT_MODIFIED, delay);
            }
        }
    }

    private static ParameterInfo getParameterInfo(MethodInfo methodInfo, int position) {
        ParameterInfo parameterInfo;
        List<ParameterInfo> params = methodInfo.methodInspection.get().getParameters();
        if (position >= params.size()) {
            ParameterInfo lastParameter = params.get(params.size() - 1);
            if (lastParameter.parameterInspection.get().isVarArgs()) {
                parameterInfo = lastParameter;
            } else {
                throw new UnsupportedOperationException("?");
            }
        } else {
            parameterInfo = params.get(position);
        }
        return parameterInfo;
    }
}
