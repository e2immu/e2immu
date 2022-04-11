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
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ExpandedVariable;
import org.e2immu.analyser.model.expression.Filter;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.InspectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class EvaluatePreconditionFromMethod {
    private static final Logger LOGGER = LoggerFactory.getLogger(EvaluatePreconditionFromMethod.class);

    public static Precondition evaluate(EvaluationResult context,
                                        EvaluationResult.Builder builder,
                                        Identifier identifierOfMethodCall,
                                        MethodInfo methodInfo,
                                        Expression scopeObject,
                                        List<Expression> parameterValues) {
        assert methodInfo != null;
        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(methodInfo);
        Precondition precondition = methodAnalysis.getPrecondition();
        assert precondition != null;

        boolean partOfCallCycle = methodInfo.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle();
        boolean callingMyself = context.getCurrentMethod() != null && methodInfo == context.getCurrentMethod().getMethodInfo();

        if (partOfCallCycle || callingMyself) {
            LOGGER.debug("Ignoring potential preconditions of method call {}, partOfCallCycle? {} calling myself? {}",
                    methodInfo.fullyQualifiedName, parameterValues, callingMyself);
            return Precondition.empty(context.getPrimitives());
        }
        if (precondition.expression().isBooleanConstant()) {
            return precondition;
        }
        if (precondition.isDelayed() || scopeObject.isDelayed()) {
            CausesOfDelay causes = scopeObject.causesOfDelay().merge(precondition.causesOfDelay());
            return Precondition.forDelayed(identifierOfMethodCall, causes, context.getPrimitives());
        }

        Expression inlinedMethod = InlinedMethod.of(identifierOfMethodCall, methodInfo, precondition.expression(), context.getAnalyserContext());
        if (inlinedMethod.isDelayed()) {
            CausesOfDelay causes = scopeObject.causesOfDelay().merge(precondition.causesOfDelay()).merge(inlinedMethod.causesOfDelay());
            return Precondition.forDelayed(identifierOfMethodCall, causes, context.getPrimitives());
        }
        InlinedMethod iv = (InlinedMethod) inlinedMethod;
        TranslationMap translationMap = iv.translationMap(context, parameterValues, scopeObject,
                context.getCurrentType(), identifierOfMethodCall);
        Expression translated = iv.translate(context.getAnalyserContext(), translationMap);
        ForwardEvaluationInfo forward = ForwardEvaluationInfo.DEFAULT.addMethod(methodInfo).copyDoNotComplainInlineConditional();
        EvaluationResult er = translated.evaluate(context, forward);
        Expression reEvaluated = er.getExpression();
        builder.compose(er);
      /*  // there is a precondition, and we have a list of values... let's see what we can learn
        // the precondition is using parameter info's as variables so we'll have to substitute
        TranslationMap translationMap = translationMap(context.evaluationContext(),
                methodInfo, parameterValues, scopeObject);

        Expression translated = precondition.expression().translate(context.getAnalyserContext(), translationMap);
        Expression reEvaluated;
        if (translated != precondition.expression()) {
            EvaluationResult eRreEvaluated = translated.evaluate(context, ForwardEvaluationInfo.DEFAULT);
            reEvaluated = eRreEvaluated.value();
            builder.composeIgnoreExpression(eRreEvaluated);
        } else {
            reEvaluated = precondition.expression();
        }*/

        // composing the effects of re-evaluation introduces the field(s) of the precondition to the statement
        // in the form of ExpandedVariable, so they cannot cause delays

        // see SetOnceMap, get() inside if(isSet()) throw new X(" "+get())
        Expression inCondition = context.evaluationContext().getConditionManager().evaluate(context, reEvaluated, false);
        if (inCondition.isDelayed()) {
            return Precondition.forDelayed(inCondition);
        }
        if (!inCondition.isBoolValueTrue()) {

            // See TestSupport SetOnce, @Mark computation in copy()
            // See Precondition_0, where there is no condition, so it doesn't matter; Precondition_6, where it does

            // from the result we either may infer another condition, or values to be set...

            // NOT_NULL on parameters -> not in PC but as annotations on the parameter
            Filter filter = new Filter(context, Filter.FilterMode.ACCEPT);
            Filter.FilterResult<ParameterInfo> filterResult = filter.filter(inCondition,
                    filter.individualNullOrNotNullClauseOnParameter());
            Map<ParameterInfo, Expression> individualNullClauses = filterResult.accepted();
            for (Map.Entry<ParameterInfo, Expression> nullClauseEntry : individualNullClauses.entrySet()) {
                if (!nullClauseEntry.getValue().equalsNull()) {
                    builder.setProperty(nullClauseEntry.getKey(), Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
                }
            }
            return new Precondition(filterResult.rest(), List.of(new Precondition.MethodCallCause(methodInfo, scopeObject)));
        }
        return Precondition.empty(context.getPrimitives());
    }

    private static TranslationMap translationMap(EvaluationContext evaluationContext,
                                                 MethodInfo methodInfo,
                                                 List<Expression> parameters,
                                                 Expression scope) {
        TranslationMapImpl.Builder builder = new TranslationMapImpl.Builder();
        List<ParameterInfo> methodParameters = methodInfo.methodInspection.get().getParameters();
        int i = 0;
        for (Expression parameterValue : parameters) {
            int indexParam = i >= methodParameters.size() ? methodParameters.size() - 1 : i;
            ParameterInfo parameterInfo = methodParameters.get(indexParam);
            Expression vv = new VariableExpression(parameterInfo);
            builder.put(vv, parameterValue);
            i++;
        }
        if (!(scope instanceof VariableExpression ve && ve.variable() instanceof This)) {
            InspectionProvider inspectionProvider = evaluationContext.getAnalyserContext();
            TypeInspection typeInspection = inspectionProvider.getTypeInspection(methodInfo.typeInfo);

            for (FieldInfo fieldInfo : typeInspection.fields()) {
                boolean staticFieldOrStaticMethod = fieldInfo.isStatic(inspectionProvider) || methodInfo.methodInspection.get().isStatic();
                FieldReference thisField = new FieldReference(inspectionProvider, fieldInfo);
                FieldReference scopeField = new FieldReference(inspectionProvider, fieldInfo, staticFieldOrStaticMethod ? null : scope, fieldInfo.owner);
                Properties properties = evaluationContext.getAnalyserContext().defaultValueProperties(scopeField.parameterizedType);
                ExpandedVariable ev = new ExpandedVariable(fieldInfo.getIdentifier(), scopeField, properties);
                if (!thisField.equals(scopeField)) {
                    builder.put(new VariableExpression(thisField), ev);
                }
            }
        }
        return builder.build();
    }
}
