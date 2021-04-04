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
import org.e2immu.analyser.model.*;
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
                                                                             MethodInfo methodInfo,
                                                                             int notModified1Scope,
                                                                             boolean recursiveOrPartOfCallCycle,
                                                                             Expression scopeObject) {
        int n = methodInfo == null ? 10 : methodInfo.methodInspection.get().getParameters().size();
        List<Expression> parameterValues = new ArrayList<>(n);
        int i = 0;
        int minNotNullOverParameters = MultiLevel.EFFECTIVELY_NOT_NULL;

        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

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
                        independent = MultiLevel.FALSE;
                    } else {
                        // copy from parameter into map used for forwarding
                        ParameterAnalysis parameterAnalysis = evaluationContext.getAnalyserContext().getParameterAnalysis(parameterInfo);
                        map = new HashMap<>();
                        map.put(VariableProperty.CONTEXT_MODIFIED, parameterAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE));
                        map.put(VariableProperty.CONTEXT_NOT_NULL, parameterAnalysis.getProperty(VariableProperty.NOT_NULL_PARAMETER));
                        map.put(VariableProperty.NOT_MODIFIED_1, parameterAnalysis.getProperty(VariableProperty.NOT_MODIFIED_1));
                        independent = parameterAnalysis.getProperty(VariableProperty.INDEPENDENT_PARAMETER);
                    }
                } catch (RuntimeException e) {
                    LOGGER.error("Failed to obtain parameter analysis of {}", parameterInfo.fullyQualifiedName());
                    throw e;
                }
                if (notModified1Scope == Level.TRUE) {
                    map.put(VariableProperty.CONTEXT_NOT_NULL, Level.FALSE);
                }
                {
                    int contextModified = map.getOrDefault(VariableProperty.CONTEXT_MODIFIED, Level.DELAY);
                    if (contextModified == Level.DELAY) {
                        if (parameterInfo.owner.isAbstract() || recursiveOrPartOfCallCycle) {
                            // we explicitly allow for a delay on CM, it triggers PROPAGATE_MODIFICATION; locally, it is non-modifying
                            map.put(VariableProperty.CONTEXT_MODIFIED, Level.FALSE);
                        } else {
                            map.put(VariableProperty.CONTEXT_MODIFIED_DELAY, Level.TRUE);
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

                if (notModified1Scope != Level.TRUE && methodInfo.isSingleAbstractMethod()) {
                    // we compute on the parameter expression, not the value (chicken and egg)
                    Boolean cannotBeModified = parameterExpression.returnType()
                            .isImplicitlyOrAtLeastEventuallyE2Immutable(evaluationContext.getAnalyserContext(),
                                    evaluationContext.getCurrentType());
                    if (cannotBeModified == null) {
                        map.put(VariableProperty.CONTEXT_MODIFIED_DELAY, Level.TRUE); // DELAY
                    } else if (cannotBeModified) {
                        map.put(VariableProperty.CONTEXT_MODIFIED, Level.FALSE);
                    }
                }

                minNotNullOverParameters = Math.min(minNotNullOverParameters, contextNotNull);

                ForwardEvaluationInfo forward = new ForwardEvaluationInfo(map, true);
                parameterResult = parameterExpression.evaluate(evaluationContext, forward);
                parameterValue = parameterResult.value();

                // @Dependent1 collection.add(t) t==parameterValue, collection=scopeObject
                if (parameterValue instanceof IsVariableExpression veArg && scopeObject instanceof IsVariableExpression veScope) {
                    if (independent == Level.DELAY) {
                        builder.link1(veArg.variable(), null); // null indicates a delay
                    } else if(independent == MultiLevel.DEPENDENT_1) {
                        builder.link1(veArg.variable(), veScope.variable());
                    }
                }

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
