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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.analyser.util.DelayDebugger;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.expression.IsVariableExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.util.Pair;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvaluateParameters {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(EvaluateParameters.class);
    private static final Map<VariableProperty, Integer> RECURSIVE_CALL =
            Map.of(VariableProperty.CONTEXT_MODIFIED, Level.FALSE,
                    VariableProperty.CONTEXT_NOT_NULL, MultiLevel.NULLABLE);

    public static Pair<EvaluationResult.Builder, List<Expression>> transform(List<Expression> parameterExpressions,
                                                                             EvaluationContext evaluationContext,
                                                                             ForwardEvaluationInfo forwardEvaluationInfo,
                                                                             MethodInfo methodInfo,
                                                                             boolean recursiveOrPartOfCallCycle,
                                                                             Expression scopeObject) {
        int n = methodInfo == null ? 10 : methodInfo.methodInspection.get().getParameters().size();
        List<Expression> parameterValues = new ArrayList<>(n);
        int i = 0;
        int minNotNullOverParameters = MultiLevel.EFFECTIVELY_NOT_NULL;

        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        builder.causeOfContextModificationDelay(methodInfo, false); // will be overwritten when there's a delay

        for (Expression parameterExpression : parameterExpressions) {
            Expression parameterValue;
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
                // NOT_NULL, NOT_MODIFIED
                int independent;
                Map<VariableProperty, Integer> map;
                try {
                    if (evaluationContext.getCurrentMethod() != null &&
                            evaluationContext.getCurrentMethod().methodInfo == methodInfo) {
                        map = new HashMap<>(RECURSIVE_CALL);
                        independent = MultiLevel.DEPENDENT;
                    } else {
                        // copy from parameter into map used for forwarding
                        ParameterAnalysis parameterAnalysis = evaluationContext.getAnalyserContext().getParameterAnalysis(parameterInfo);
                        map = new HashMap<>();
                        map.put(VariableProperty.CONTEXT_MODIFIED, parameterAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE));
                        map.put(VariableProperty.CONTEXT_NOT_NULL, parameterAnalysis.getProperty(VariableProperty.NOT_NULL_PARAMETER));
                        map.put(VariableProperty.CONTAINER, parameterAnalysis.getProperty(VariableProperty.CONTAINER));
                        independent = parameterAnalysis.getProperty(VariableProperty.INDEPENDENT);
                    }
                } catch (RuntimeException e) {
                    LOGGER.error("Failed to obtain parameter analysis of {}", parameterInfo.fullyQualifiedName());
                    throw e;
                }

                // propagating modifications? a functional interface-type parameter, with @Independent1
                if (parameterInfo.parameterizedType.isFunctionalInterface(evaluationContext.getAnalyserContext())) {
                    if (independent == Level.DELAY) {
                        if (parameterInfo.owner.isAbstract() || recursiveOrPartOfCallCycle) {
                            // we explicitly allow for a delay on CM, it triggers PROPAGATE_MODIFICATION; locally, it is non-modifying
                            map.put(VariableProperty.PROPAGATE_MODIFICATION, Level.FALSE);
                        } else {
                            map.put(VariableProperty.PROPAGATE_MODIFICATION_DELAY, Level.TRUE);
                        }
                    } else if (independent == MultiLevel.INDEPENDENT_1) {
                        map.put(VariableProperty.PROPAGATE_MODIFICATION, Level.TRUE);
                    }
                }

                {
                    int contextModified = map.getOrDefault(VariableProperty.CONTEXT_MODIFIED, Level.DELAY);
                    if (contextModified == Level.DELAY) {
                        if (parameterInfo.owner.isAbstract() || recursiveOrPartOfCallCycle) {
                            // we explicitly allow for a delay on CM, it triggers PROPAGATE_MODIFICATION; locally, it is non-modifying
                            map.put(VariableProperty.CONTEXT_MODIFIED, Level.FALSE);
                        } else {
                            map.put(VariableProperty.CONTEXT_MODIFIED_DELAY, Level.TRUE);
                            assert evaluationContext.createDelay(parameterInfo.fullyQualifiedName(),
                                    methodInfo.fullyQualifiedName + DelayDebugger.D_CAUSES_OF_CONTENT_MODIFICATION_DELAY);
                            builder.causeOfContextModificationDelay(methodInfo, true);
                        }
                    }
                }
                int contextNotNull = map.getOrDefault(VariableProperty.CONTEXT_NOT_NULL, Level.DELAY);
                if (contextNotNull == Level.DELAY) {
                    if (recursiveOrPartOfCallCycle) {
                        map.put(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.NULLABLE); // won't be me to rock the boat
                    } else {
                        map.put(VariableProperty.CONTEXT_NOT_NULL_DELAY, Level.TRUE);
                    }
                }

                minNotNullOverParameters = Math.min(minNotNullOverParameters, contextNotNull);

                ForwardEvaluationInfo forward = new ForwardEvaluationInfo(map, true,
                        forwardEvaluationInfo.assignmentTarget());
                parameterResult = parameterExpression.evaluate(evaluationContext, forward);
                parameterValue = parameterResult.value();
            } else {
                parameterResult = parameterExpression.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
                parameterValue = parameterResult.value();
            }

            builder.compose(parameterResult);
            parameterValues.add(parameterValue);
            i++;
        }

        VariableExpression scopeVariable;
        if (minNotNullOverParameters == MultiLevel.EFFECTIVELY_NOT_NULL &&
                i > 0 &&
                methodInfo != null &&
                scopeObject != null &&
                methodInfo.typeInfo.typeInspection.get().isFunctionalInterface() &&
                (scopeVariable = scopeObject.asInstanceOf(VariableExpression.class)) != null) {
            builder.setProperty(scopeVariable.variable(), VariableProperty.CONTEXT_NOT_NULL,
                    MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL);
        }
        return new Pair<>(builder, parameterValues);
    }
}
