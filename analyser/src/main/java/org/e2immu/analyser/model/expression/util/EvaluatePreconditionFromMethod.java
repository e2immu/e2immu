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
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.Filter;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.List;
import java.util.Map;

public class EvaluatePreconditionFromMethod {

    public static void evaluate(EvaluationContext evaluationContext,
                                EvaluationResult.Builder builder,
                                MethodInfo methodInfo,
                                Expression scopeObject,
                                List<Expression> parameterValues) {

        MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo);
        Expression precondition = methodAnalysis.getPrecondition();
        if (precondition == null) {
            boolean partOfCallCycle = methodInfo != null && methodInfo.partOfCallCycle();
            boolean callingMyself = evaluationContext.getCurrentMethod() != null &&
                    methodInfo == evaluationContext.getCurrentMethod().methodInfo;
            if (!partOfCallCycle && !callingMyself) builder.addDelayOnPrecondition();
        } else if (!precondition.isBooleanConstant()) {
            boolean scopeDelayed = evaluationContext.isDelayed(scopeObject);
            if (scopeDelayed) {
                builder.addDelayOnPrecondition();
                return;
            }
            // there is a precondition, and we have a list of values... let's see what we can learn
            // the precondition is using parameter info's as variables so we'll have to substitute
            Map<Expression, Expression> translationMap = translationMap(evaluationContext.getAnalyserContext(),
                    methodInfo, parameterValues, scopeObject);
            EvaluationResult eRreEvaluated = precondition.reEvaluate(evaluationContext, translationMap);
            Expression reEvaluated = eRreEvaluated.value();
            builder.compose(eRreEvaluated);

            // from the result we either may infer another condition, or values to be set...

            // NOT_NULL
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
            Expression rest = filterResult.rest();
            builder.addUntranslatedPrecondition(rest);
            Expression translated = evaluationContext.acceptAndTranslatePrecondition(rest);
            if (translated != null) {
                builder.addPrecondition(translated);
            }
        }
    }

    public static Map<Expression, Expression> translationMap(InspectionProvider inspectionProvider,
                                                             MethodInfo methodInfo,
                                                             List<Expression> parameters,
                                                             Expression scope) {
        ImmutableMap.Builder<Expression, Expression> builder = new ImmutableMap.Builder<>();
        int i = 0;
        for (Expression parameterValue : parameters) {
            ParameterInfo parameterInfo = methodInfo.methodInspection.get().getParameters().get(i);
            Expression vv = new VariableExpression(parameterInfo, parameterValue.getObjectFlow());
            builder.put(vv, parameterValue);
            i++;
        }
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(methodInfo.typeInfo);
        if (scope instanceof VariableExpression ve) {
            This thisVar = new This(inspectionProvider, methodInfo.typeInfo);
            for (FieldInfo fieldInfo : typeInspection.fields()) {
                FieldReference thisField = new FieldReference(inspectionProvider, fieldInfo, thisVar);
                FieldReference scopeField = new FieldReference(inspectionProvider, fieldInfo, ve.variable());
                builder.put(new VariableExpression(thisField), new VariableExpression(scopeField));
            }
        }
        return builder.build();
    }
}
