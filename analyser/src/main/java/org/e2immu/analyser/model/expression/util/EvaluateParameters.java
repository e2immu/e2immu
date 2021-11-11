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
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.util.Pair;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvaluateParameters {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(EvaluateParameters.class);
    private static final Map<VariableProperty, DV> RECURSIVE_CALL =
            Map.of(VariableProperty.CONTEXT_MODIFIED, Level.FALSE_DV,
                    VariableProperty.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV);

    public static Pair<EvaluationResult.Builder, List<Expression>> transform(List<Expression> parameterExpressions,
                                                                             EvaluationContext evaluationContext,
                                                                             ForwardEvaluationInfo forwardEvaluationInfo,
                                                                             MethodInfo methodInfo,
                                                                             boolean recursiveOrPartOfCallCycle,
                                                                             Expression scopeObject) {
        int n = methodInfo == null ? 10 : methodInfo.methodInspection.get().getParameters().size();
        List<Expression> parameterValues = new ArrayList<>(n);
        int i = 0;
        DV minNotNullOverParameters = MultiLevel.EFFECTIVELY_NOT_NULL_DV;

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
                DV independent;
                Map<VariableProperty, DV> map;
                try {
                    if (evaluationContext.getCurrentMethod() != null &&
                            evaluationContext.getCurrentMethod().methodInfo == methodInfo) {
                        map = new HashMap<>(RECURSIVE_CALL);
                        independent = MultiLevel.DEPENDENT_DV;
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
                    if (independent.isDelayed()) {
                        if (parameterInfo.owner.isAbstract() || recursiveOrPartOfCallCycle) {
                            // we explicitly allow for a delay on CM, it triggers PROPAGATE_MODIFICATION; locally, it is non-modifying
                            map.put(VariableProperty.PROPAGATE_MODIFICATION, Level.FALSE_DV);
                        } else {
                            //   map.put(VariableProperty.PROPAGATE_MODIFICATION_DELAY, Level.TRUE_DV);
                        }
                    } else if (independent.value() == MultiLevel.INDEPENDENT_1) {
                        map.put(VariableProperty.PROPAGATE_MODIFICATION, Level.TRUE_DV);
                    }
                }

                {
                    DV contextModified = map.getOrDefault(VariableProperty.CONTEXT_MODIFIED, null);
                    if (contextModified == null) {
                        if (parameterInfo.owner.isAbstract() || recursiveOrPartOfCallCycle) {
                            // we explicitly allow for a delay on CM, it triggers PROPAGATE_MODIFICATION; locally, it is non-modifying
                            map.put(VariableProperty.CONTEXT_MODIFIED, Level.FALSE_DV);
                        } else {
                            //  map.put(VariableProperty.CONTEXT_MODIFIED_DELAY, Level.TRUE_DV);
                            builder.causeOfContextModificationDelay(methodInfo, true);
                        }
                    }
                }
                DV contextNotNull = map.getOrDefault(VariableProperty.CONTEXT_NOT_NULL, null);
                if (contextNotNull.isDelayed() && recursiveOrPartOfCallCycle) {
                    map.put(VariableProperty.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV); // won't be me to rock the boat
                }

                minNotNullOverParameters = minNotNullOverParameters.min(contextNotNull);

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
        if (minNotNullOverParameters.equals(MultiLevel.EFFECTIVELY_NOT_NULL_DV) &&
                i > 0 &&
                methodInfo != null &&
                scopeObject != null &&
                methodInfo.typeInfo.typeInspection.get().isFunctionalInterface() &&
                (scopeVariable = scopeObject.asInstanceOf(VariableExpression.class)) != null) {
            builder.setProperty(scopeVariable.variable(), VariableProperty.CONTEXT_NOT_NULL,
                    MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV);
        }
        return new Pair<>(builder, parameterValues);
    }
}
