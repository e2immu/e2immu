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
import org.e2immu.analyser.analyser.HiddenContentSelector;
import org.e2immu.analyser.analyser.impl.ComputeIndependentImpl;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analyser.ComputeIndependent;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.LV.*;
import static org.e2immu.analyser.model.MultiLevel.*;

public class MethodLinkHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodLinkHelper.class);

    private final EvaluationResult context;
    private final MethodAnalysis methodAnalysis;
    private final MethodInfo methodInfo;
    private final ComputeIndependent computeIndependent;

    public MethodLinkHelper(EvaluationResult context, MethodInfo methodInfo) {
        this(context, methodInfo, context.getAnalyserContext().getMethodAnalysis(methodInfo),
                new ComputeIndependentImpl(context.getAnalyserContext(), context.getCurrentType()));
    }

    public MethodLinkHelper(EvaluationResult context, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        this(context, methodInfo, methodAnalysis,
                new ComputeIndependentImpl(context.getAnalyserContext(), context.getCurrentType()));
    }


    public MethodLinkHelper(EvaluationResult context, MethodInfo methodInfo, MethodAnalysis methodAnalysis,
                            ComputeIndependent computeIndependent) {
        this.context = context;
        this.methodInfo = methodInfo;
        this.methodAnalysis = methodAnalysis;
        this.computeIndependent = computeIndependent;
    }

    /*
    called by ConstructorCall and MethodCall
     */
    private List<LinkedVariables> computeLinkedVariablesOfParameters(List<EvaluationResult> parameterResults) {
        // temporary assertion, to help debugging
        assert parameterResults.stream().noneMatch(er -> er.linkedVariablesOfExpression() == null);
        return parameterResults.stream().map(er -> er.linkedVariablesOfExpression().maximum(LINK_DEPENDENT)).toList();
    }

    public record LambdaResult(List<LinkedVariables> linkedToParameters, LinkedVariables linkedToReturnValue) {
        public LinkedVariables delay(CausesOfDelay causesOfDelay) {
            return linkedToParameters.stream().reduce(LinkedVariables.EMPTY, LinkedVariables::merge)
                    .merge(linkedToReturnValue.changeToDelay(LV.delay(causesOfDelay)));
        }

        public LinkedVariables mergedLinkedToParameters() {
            return linkedToParameters.stream().reduce(LinkedVariables.EMPTY, LinkedVariables::merge);
        }
    }

    public static LambdaResult lambdaLinking(EvaluationContext evaluationContext, MethodInfo concreteMethod) {

        MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(concreteMethod);
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        if (lastStatement == null) {
            return new LambdaResult(List.of(), LinkedVariables.EMPTY);
        }
        MethodInspection methodInspection = evaluationContext.getAnalyserContext().getMethodInspection(concreteMethod);
        List<LinkedVariables> result = new ArrayList<>(methodInspection.getParameters().size() + 1);

        for (ParameterInfo pi : methodInspection.getParameters()) {
            VariableInfo vi = lastStatement.getLatestVariableInfo(pi.fullyQualifiedName);
            LinkedVariables lv = vi.getLinkedVariables().remove(v ->
                    !evaluationContext.acceptForVariableAccessReport(v, concreteMethod.typeInfo));
            result.add(lv);
        }
        if (concreteMethod.hasReturnValue()) {
            ReturnVariable returnVariable = new ReturnVariable(concreteMethod);
            VariableInfo vi = lastStatement.getLatestVariableInfo(returnVariable.fqn);
            return new LambdaResult(result, vi.getLinkedVariables());
        }
        return new LambdaResult(result, LinkedVariables.EMPTY);
    }

    public record FromParameters(EvaluationResult intoObject, EvaluationResult intoResult) {
    }

    /*
    Add all necessary links from parameters into scope, and in-between parameters
     */
    public FromParameters linksInvolvingParameters(ParameterizedType objectPt,
                                                   ParameterizedType resultPt,
                                                   List<EvaluationResult> parameterResults) {
        MethodInspection methodInspection = context.getAnalyserContext().getMethodInspection(methodInfo);
        EvaluationResultImpl.Builder intoObjectBuilder = new EvaluationResultImpl.Builder(context)
                .setLinkedVariablesOfExpression(LinkedVariables.EMPTY);
        EvaluationResultImpl.Builder intoResultBuilder = resultPt == null || resultPt.isVoid() ? null
                : new EvaluationResultImpl.Builder(context).setLinkedVariablesOfExpression(LinkedVariables.EMPTY);

        if (!methodInspection.getParameters().isEmpty()) {
            List<LinkedVariables> parameterLv = computeLinkedVariablesOfParameters(parameterResults);

            // links between object/return value and parameters
            for (ParameterAnalysis parameterAnalysis : methodAnalysis.getParameterAnalyses()) {
                DV formalParameterIndependent = parameterAnalysis.getProperty(Property.INDEPENDENT);
                LinkedVariables lvsToResult = parameterAnalysis.getLinkToReturnValueOfMethod();
                if (!INDEPENDENT_DV.equals(formalParameterIndependent) || !lvsToResult.isEmpty()) {
                    ParameterInfo pi = parameterAnalysis.getParameterInfo();
                    ParameterizedType parameterType = pi.parameterizedType;
                    LinkedVariables parameterLvs;
                    boolean inResult;
                    if (!lvsToResult.isEmpty()) {
                        /*
                        change the links of the parameter to the value of the return variable (see also MethodReference,
                        computation of links when modified is true)
                         */
                        inResult = intoResultBuilder != null;
                        LinkedVariables returnValueLvs = parameterLv.get(pi.index);
                        LV valueOfReturnValue = lvsToResult.stream().filter(e -> e.getKey() instanceof ReturnVariable)
                                .map(Map.Entry::getValue).findFirst().orElseThrow();
                        Map<Variable, LV> map = returnValueLvs.stream().collect(Collectors.toMap(Map.Entry::getKey,
                                e -> {
                                    if (e.getValue().isCommonHC() && valueOfReturnValue.isCommonHC()) {
                                        return e.getValue();
                                    }
                                    return e.getValue().min(valueOfReturnValue);
                                }));
                        parameterLvs = LinkedVariables.of(map);
                        formalParameterIndependent = valueOfReturnValue.isCommonHC() ? INDEPENDENT_HC_DV : DEPENDENT_DV;
                    } else {
                        inResult = false;
                        parameterLvs = parameterLv.get(pi.index);
                    }
                    ParameterizedType pt = inResult ? resultPt : objectPt;
                    if (pt != null) {
                        LinkedVariables lv = computeIndependent.linkedVariables(parameterType, parameterLvs, null,
                                formalParameterIndependent, parameterAnalysis.getHiddenContentSelector(), pt);
                        EvaluationResultImpl.Builder builder = inResult ? intoResultBuilder : intoObjectBuilder;
                        builder.mergeLinkedVariablesOfExpression(lv);
                    }
                }
            }

            linksBetweenParameters(intoObjectBuilder, methodInfo, parameterResults, parameterLv);
        }
        return new FromParameters(intoObjectBuilder.build(), intoResultBuilder == null ? null :
                intoResultBuilder.build());
    }

    public void linksBetweenParameters(EvaluationResultImpl.Builder builder,
                                       MethodInfo methodInfo,
                                       List<EvaluationResult> parameterResults,
                                       List<LinkedVariables> parameterLvs) {
        Map<ParameterInfo, LinkedVariables> crossLinks = methodInfo.crossLinks(context.getAnalyserContext());
        if (crossLinks.isEmpty()) return;
        crossLinks.forEach((pi, lv) -> lv.stream().forEach(e -> {
            ParameterInfo target = (ParameterInfo) e.getKey();
            boolean sourceIsVarArgs = pi.parameterInspection.get().isVarArgs();
            assert !sourceIsVarArgs : "Varargs must always be a target";
            boolean targetIsVarArgs = target.parameterInspection.get().isVarArgs();
            if (!targetIsVarArgs || parameterResults.size() > target.index) {
                ParameterizedType atIndex = parameterResults.get(target.index).getExpression().returnType();
                ParameterizedType concreteTargetType;
                if (targetIsVarArgs && parameterResults.size() > target.index + 1) {
                    concreteTargetType = parameterResults.subList(target.index + 1, parameterResults.size()).stream()
                            .map(er -> er.getExpression().returnType())
                            .reduce(atIndex, (pt1, pt2) -> pt1.commonType(context.getAnalyserContext(), pt2));
                } else {
                    concreteTargetType = atIndex;
                }
                LV level = e.getValue();
                LinkedVariables sourceLvs = parameterLvs.get(pi.index);
                tryLinkBetweenParameters(builder, target.index, targetIsVarArgs, concreteTargetType,
                        level, pi.parameterizedType, sourceLvs, parameterLvs);
            } // else: no value... empty varargs
        }));
    }

    //
    //example Independent1_2_1
    //target = ts, index 0, not varargs, linked 4=common_hc to generator, index 1;
    //target ts is modified; values are new String[4] and generator, linked variables are this.ts:2 and generator:2
    //
    private void tryLinkBetweenParameters(EvaluationResultImpl.Builder builder,
                                          int targetIndex,
                                          boolean targetIsVarArgs,
                                          ParameterizedType targetType,
                                          LV level,
                                          ParameterizedType sourceType,
                                          LinkedVariables sourceLinkedVariables,
                                          List<LinkedVariables> parameterLvs) {
        LinkedVariables mergedLvs;
        HiddenContentSelector hcsSource = level.isCommonHC() ? level.theirs() : HiddenContentSelector.None.INSTANCE;
        HiddenContentSelector hcsTarget = level.isCommonHC() ? level.mine() : HiddenContentSelector.None.INSTANCE;
        DV independentDv = level.isCommonHC() ? INDEPENDENT_HC_DV : DEPENDENT_DV;
        if (targetIsVarArgs) {
            mergedLvs = LinkedVariables.EMPTY;
            for (int i = targetIndex; i < parameterLvs.size(); i++) {
                LinkedVariables lvs = parameterLvs.get(i);
                LinkedVariables lv = computeIndependent.linkedVariables(targetType, lvs, hcsSource, independentDv, hcsTarget, sourceType);
                mergedLvs = mergedLvs.merge(lv);
            }
        } else {
            LinkedVariables targetLinkedVariables = parameterLvs.get(targetIndex);
            mergedLvs = computeIndependent.linkedVariables(targetType, targetLinkedVariables, hcsSource, independentDv, hcsTarget, sourceType);
        }
        LinkedVariables finalMergedLvs = mergedLvs;
        sourceLinkedVariables.stream().forEach(e ->
                finalMergedLvs.stream().forEach(e2 ->
                        builder.link(e.getKey(), e2.getKey(), e.getValue().max(e2.getValue()))
                ));
    }

    /*
   In general, the method result 'a', in 'a = b.method(c, d)', can link to 'b', 'c' and/or 'd'.
   Independence and immutability restrict the ability to link.

   The current implementation is heavily focused on understanding links towards the fields of a type,
   i.e., in sub = list.subList(0, 10), we want to link sub to list.

   Links from the parameters to the result (from 'c' to 'a', from 'd' to 'a') have currently only
   been implemented for @Identity methods (i.e., between 'a' and 'c').

   So we implement
   1/ void methods cannot link
   2/ if the method is @Identity, the result is linked to the 1st parameter 'c'
   3/ if the method is a factory method, the result is linked to the parameter values

   all other rules now determine whether we return an empty set, or the set {'a'}.

   4/ independence is determined by the independence value of the method, and the independence value of the object 'a'
    */

    public LinkedVariables linkedVariablesMethodCallObjectToReturnType(EvaluationResult objectResult,
                                                                       List<EvaluationResult> parameterResults,
                                                                       ParameterizedType concreteReturnType) {
        // RULE 1: void method cannot link
        if (methodInfo.noReturnValue()) return LinkedVariables.EMPTY;
        boolean recursiveCall = recursiveCall(methodInfo, context.evaluationContext());
        boolean breakCallCycleDelay = methodInfo.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle();
        if (recursiveCall || breakCallCycleDelay) {
            return LinkedVariables.EMPTY;
        }
        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(methodInfo);

        // RULE 2: @Identity links to the 1st parameter
        DV identity = methodAnalysis.getProperty(Property.IDENTITY);
        if (identity.valueIsTrue()) {
            return parameterResults.get(0).linkedVariablesOfExpression().maximum(LINK_ASSIGNED);
        }
        LinkedVariables linkedVariablesOfObject = objectResult.linkedVariablesOfExpression()
                .maximum(LINK_ASSIGNED); // should be delay-able!

        if (identity.isDelayed() && !parameterResults.isEmpty()) {
            // temporarily link to both the object and the parameter, in a delayed way
            LinkedVariables allParams = parameterResults.stream()
                    .map(EvaluationResult::linkedVariablesOfExpression)
                    .reduce(LinkedVariables.EMPTY, LinkedVariables::merge);
            return linkedVariablesOfObject
                    .merge(allParams)
                    .changeNonStaticallyAssignedToDelay(identity.causesOfDelay());
        }

        // RULE 3: @Fluent simply returns the same object, hence, the same linked variables
        DV fluent = methodAnalysis.getMethodProperty(Property.FLUENT);
        if (fluent.valueIsTrue()) {
            return linkedVariablesOfObject;
        }
        if (fluent.isDelayed()) {
            return linkedVariablesOfObject.changeNonStaticallyAssignedToDelay(fluent.causesOfDelay());
        }

        DV independent = methodAnalysis.getProperty(Property.INDEPENDENT);
        return computeIndependent.linkedVariables(objectResult.getExpression().returnType(), linkedVariablesOfObject,
                null, independent, methodAnalysis.getHiddenContentSelector(), concreteReturnType);
    }

       /* we have to probe the object first, to see if there is a value
       A. if there is a value, and the value offers a concrete implementation, we replace methodInfo by that
       concrete implementation.
       B. if there is no value, and the delay indicates that a concrete implementation may be forthcoming,
       we delay
       C otherwise (no value, no concrete implementation forthcoming) we continue with the abstract method.
       */

    public static boolean recursiveCall(MethodInfo methodInfo, EvaluationContext evaluationContext) {
        MethodAnalyser currentMethod = evaluationContext.getCurrentMethod();
        if (currentMethod != null && currentMethod.getMethodInfo() == methodInfo) return true;
        if (evaluationContext.getClosure() != null) {
            LOGGER.debug("Going recursive on call to {}, to {} ", methodInfo.fullyQualifiedName,
                    evaluationContext.getClosure().getCurrentType().fullyQualifiedName);
            return recursiveCall(methodInfo, evaluationContext.getClosure());
        }
        return false;
    }
}
