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
import org.e2immu.analyser.analyser.Precondition;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.Filter;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvaluatePreconditionFromMethod {

    public static void evaluate(EvaluationContext evaluationContext,
                                EvaluationResult.Builder builder,
                                MethodInfo methodInfo,
                                Expression scopeObject,
                                List<Expression> parameterValues) {

        MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo);
        Precondition precondition = methodAnalysis.getPrecondition();
        if (precondition == null) {
            boolean partOfCallCycle = methodInfo != null && methodInfo.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle();
            boolean callingMyself = evaluationContext.getCurrentMethod() != null &&
                    methodInfo == evaluationContext.getCurrentMethod().methodInfo;
            if (!partOfCallCycle && !callingMyself) builder.addDelayOnPrecondition();
        } else if (!precondition.expression().isBooleanConstant()) {
            boolean scopeDelayed = evaluationContext.isDelayed(scopeObject);
            if (scopeDelayed) {
                builder.addDelayOnPrecondition();
                return;
            }
            // there is a precondition, and we have a list of values... let's see what we can learn
            // the precondition is using parameter info's as variables so we'll have to substitute
            Map<Expression, Expression> translationMap = translationMap(evaluationContext.getAnalyserContext(),
                    methodInfo, parameterValues, scopeObject);
            Expression reEvaluated;
            if (!translationMap.isEmpty()) {
                EvaluationResult eRreEvaluated = precondition.expression().reEvaluate(evaluationContext, translationMap);
                reEvaluated = eRreEvaluated.value();
                builder.composeIgnoreExpression(eRreEvaluated);
            } else {
                reEvaluated = precondition.expression();
            }
            // composing the effects of re-evaluation introduces the field(s) of the precondition to the statement
            // the result of the re-evaluation may cause delays

            // see SetOnceMap, get() inside if(isSet()) throw new X(" "+get())
            Expression inCondition = evaluationContext.getConditionManager().evaluate(evaluationContext, reEvaluated);
            if (!inCondition.isBoolValueTrue()) {

                // from the result we either may infer another condition, or values to be set...

                // NOT_NULL on parameters -> not in PC but as annotations on the parameter
                Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
                Filter.FilterResult<ParameterInfo> filterResult = filter.filter(reEvaluated,
                        filter.individualNullOrNotNullClauseOnParameter());
                Map<ParameterInfo, Expression> individualNullClauses = filterResult.accepted();
                for (Map.Entry<ParameterInfo, Expression> nullClauseEntry : individualNullClauses.entrySet()) {
                    if (!nullClauseEntry.getValue().equalsNull()) {
                        builder.setProperty(nullClauseEntry.getKey(), VariableProperty.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
                    }
                }

                // all the rest: preconditions
                Expression translated = evaluationContext.acceptAndTranslatePrecondition(filterResult.rest());
                if (translated != null) {
                    builder.addPrecondition(new Precondition(translated,
                            List.of(new Precondition.MethodCallCause(methodInfo, scopeObject))));
                }
            }
        }
    }

    private static Map<Expression, Expression> translationMap(InspectionProvider inspectionProvider,
                                                              MethodInfo methodInfo,
                                                              List<Expression> parameters,
                                                              Expression scope) {
        Map<Expression, Expression> builder = new HashMap<>();
        List<ParameterInfo> methodParameters = methodInfo.methodInspection.get().getParameters();
        int i = 0;
        for (Expression parameterValue : parameters) {
            int indexParam = i >= methodParameters.size() ? methodParameters.size() - 1 : i;
            ParameterInfo parameterInfo = methodParameters.get(indexParam);
            Expression vv = new VariableExpression(parameterInfo);
            builder.put(vv, parameterValue);
            i++;
        }
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(methodInfo.typeInfo);
        VariableExpression ve;
        if ((ve = scope.asInstanceOf(VariableExpression.class)) != null) {
            for (FieldInfo fieldInfo : typeInspection.fields()) {
                boolean staticField = fieldInfo.isStatic(inspectionProvider);
                FieldReference thisField = new FieldReference(inspectionProvider, fieldInfo);
                FieldReference scopeField = new FieldReference(inspectionProvider, fieldInfo, staticField ? null : ve);
                if (!thisField.equals(scopeField)) {
                    builder.put(new VariableExpression(thisField), new VariableExpression(scopeField));
                }
            }
        }
        return Map.copyOf(builder);
    }
}
