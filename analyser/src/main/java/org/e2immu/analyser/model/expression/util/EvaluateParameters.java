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
                                                                             EvaluationContext evaluationContext,
                                                                             ForwardEvaluationInfo forwardEvaluationInfo,
                                                                             MethodInfo methodInfo,
                                                                             boolean recursiveOrPartOfCallCycle,
                                                                             Expression scopeObject) {
        int n = methodInfo == null ? 10 : methodInfo.methodInspection.get().getParameters().size();
        List<Expression> parameterValues = new ArrayList<>(n);
        int i = 0;
        DV minCnnOverParameters = MultiLevel.EFFECTIVELY_NOT_NULL_DV;

        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

        for (Expression parameterExpression : parameterExpressions) {
            minCnnOverParameters = oneParameterReturnCnn(evaluationContext, forwardEvaluationInfo,
                    methodInfo, recursiveOrPartOfCallCycle, parameterValues, i, builder, parameterExpression);
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

    private static DV oneParameterReturnCnn(EvaluationContext evaluationContext,
                                            ForwardEvaluationInfo forwardEvaluationInfo,
                                            MethodInfo methodInfo,
                                            boolean recursiveOrPartOfCallCycle,
                                            List<Expression> parameterValues,
                                            int position,
                                            EvaluationResult.Builder builder,
                                            Expression parameterExpression) {
        Expression parameterValue;
        EvaluationResult parameterResult;
        DV contextNotNull;
        if (methodInfo != null) {
            ParameterInfo parameterInfo = getParameterInfo(methodInfo, position);
            // NOT_NULL, NOT_MODIFIED
            DV independent;
            Map<Property, DV> map;
            try {
                if (evaluationContext.getCurrentMethod() != null &&
                        evaluationContext.getCurrentMethod().getMethodInfo() == methodInfo) {
                    map = new HashMap<>(RECURSIVE_CALL);
                    independent = MultiLevel.DEPENDENT_DV;
                } else {
                    // copy from parameter into map used for forwarding
                    ParameterAnalysis parameterAnalysis = evaluationContext.getAnalyserContext().getParameterAnalysis(parameterInfo);
                    map = new HashMap<>();
                    map.put(Property.CONTEXT_MODIFIED, parameterAnalysis.getProperty(Property.MODIFIED_VARIABLE));
                    map.put(Property.CONTEXT_NOT_NULL, parameterAnalysis.getProperty(Property.NOT_NULL_PARAMETER));
                    map.put(Property.CONTAINER, parameterAnalysis.getProperty(Property.CONTAINER));
                    independent = parameterAnalysis.getProperty(Property.INDEPENDENT);
                }
            } catch (RuntimeException e) {
                LOGGER.error("Failed to obtain parameter analysis of {}", parameterInfo.fullyQualifiedName());
                throw e;
            }

            // propagating modifications? a functional interface-type parameter, with @Independent1
            if (parameterInfo.parameterizedType.isFunctionalInterface(evaluationContext.getAnalyserContext())) {
                doPropagateModification(recursiveOrPartOfCallCycle, parameterInfo, independent, map);
            }

            doContextModified(methodInfo, recursiveOrPartOfCallCycle, parameterInfo, map);
            contextNotNull = map.getOrDefault(Property.CONTEXT_NOT_NULL, null);
            if (contextNotNull.isDelayed() && recursiveOrPartOfCallCycle) {
                map.put(Property.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV); // won't be me to rock the boat
            }

            ForwardEvaluationInfo forward = new ForwardEvaluationInfo(map, true,
                    forwardEvaluationInfo.assignmentTarget());
            parameterResult = parameterExpression.evaluate(evaluationContext, forward);
            parameterValue = parameterResult.value();
        } else {
            parameterResult = parameterExpression.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
            parameterValue = parameterResult.value();
            contextNotNull = Property.CONTEXT_NOT_NULL.bestDv;
        }

        builder.compose(parameterResult);
        parameterValues.add(parameterValue);
        return contextNotNull;
    }

    private static void doPropagateModification(boolean recursiveOrPartOfCallCycle,
                                                ParameterInfo parameterInfo,
                                                DV independent,
                                                Map<Property, DV> map) {
        if (independent.isDelayed()) {
            DV pm;
            if (parameterInfo.owner.isAbstract() || recursiveOrPartOfCallCycle) {
                // we explicitly allow for a delay on CM, it triggers PROPAGATE_MODIFICATION; locally, it is non-modifying
                pm = DV.FALSE_DV;
            } else {
                pm = parameterInfo.delay(CauseOfDelay.Cause.PROP_MOD);
            }
            map.put(Property.PROPAGATE_MODIFICATION, pm);
        } else if (independent.equals(MultiLevel.INDEPENDENT_1_DV)) {
            map.put(Property.PROPAGATE_MODIFICATION, DV.TRUE_DV);
        }
    }

    private static void doContextModified(MethodInfo methodInfo,
                                          boolean recursiveOrPartOfCallCycle,
                                          ParameterInfo parameterInfo,
                                          Map<Property, DV> map) {
        DV contextModified = map.getOrDefault(Property.CONTEXT_MODIFIED, null);
        if (contextModified == null) {
            DV cm;
            if (parameterInfo.owner.isAbstract() || recursiveOrPartOfCallCycle) {
                // we explicitly allow for a delay on CM, it triggers PROPAGATE_MODIFICATION; locally, it is non-modifying
                cm = DV.FALSE_DV;
            } else {
                cm = methodInfo.delay(CauseOfDelay.Cause.CONTEXT_MODIFIED);
            }
            map.put(Property.CONTEXT_MODIFIED, cm);
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
