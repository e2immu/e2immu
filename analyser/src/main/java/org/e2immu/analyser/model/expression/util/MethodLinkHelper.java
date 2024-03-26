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
import org.e2immu.analyser.analyser.impl.ComputeIndependentImpl;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analyser.ComputeIndependent;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.inspector.MethodTypeParameterMap;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.This;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        return parameterResults.stream().map(er -> er.linkedVariablesOfExpression().maximum(LINK_DEPENDENT)).toList();
    }

    /*
    TODO this code must move to evaluation of the respective types
    situations:

    1. consumer, as in .forEach(Consumer<T> consumer)
    e.g. in tests Independent11_1, FactoryMethod_0, UpgradableBooleanMap

    if the parameter of the accept method of the consumer links to a variable accessible here, then we extend the
    link to the object of the method call.
    Example:  list.stream().forEach(e -> result.add(e));
    e links --3--> into 'result', so we'll link list <--4--> result

    2. supplier, as in .fill(Supplier<T> supplier)

    3. function, with ins and outs
    */
    private static List<LinkedVariables> additionalLinkingFunctionalInterface2(EvaluationResult context,
                                                                               Expression parameterExpression,
                                                                               EvaluationResult parameterResult) {
        MethodInfo methodInfo;
        TypeInfo nestedType;
        ConstructorCall cc;
        MethodReference methodReference;
        Instance instance;
        Lambda lambda;

        if ((lambda = parameterExpression.asInstanceOf(Lambda.class)) != null) {
            methodInfo = lambda.methodInfo;
            nestedType = lambda.methodInfo.typeInfo;
        } else if ((cc = parameterExpression.asInstanceOf(ConstructorCall.class)) != null
                   && cc.anonymousClass() != null) {
            TypeInspection anonymousClassInspection = context.getAnalyserContext().getTypeInspection(cc.anonymousClass());
            assert parameterExpression.returnType().isFunctionalInterface();
            methodInfo = anonymousClassInspection.findMethodOverridingSAMOf(parameterExpression.returnType().typeInfo);
            nestedType = cc.anonymousClass();
        } else if ((methodReference = parameterExpression.asInstanceOf(MethodReference.class)) != null) {
            Expression parameterValue = parameterResult.value();
            MethodReference mr;
            if ((mr = parameterValue.asInstanceOf(MethodReference.class)) != null) {
                // do we have access to the code?
                if (methodReference.methodInfo.typeInfo.primaryType().equals(context.getCurrentType().primaryType())) {
                    methodInfo = methodReference.methodInfo;
                    // yes, access to the code!!
                    List<LinkedVariables> res = additionalLinkingConsumer(context.evaluationContext(),
                            methodReference.methodInfo, null);
                    if (mr.scope.isInstanceOf(TypeExpression.class)) {
                        /*
                         methods which only have a scope, no argument... could be static suppliers, but more likely
                         non-modifying getters. Ignoring for now.
                         */
                        return List.of();
                    }
                    IsVariableExpression ive = mr.scope.asInstanceOf(IsVariableExpression.class);
                    if (!(ive != null && ive.variable() instanceof This thisVar
                          && thisVar.typeInfo == context.getCurrentType())) {
                        This innerThis = new This(context.getAnalyserContext(), methodInfo.typeInfo);
                        if (ive != null) {
                            TranslationMap tm = new TranslationMapImpl.Builder().put(innerThis, ive.variable()).build();
                            return res.stream()
                                    .map(lv -> lv.translate(context.getAnalyserContext(), tm))
                                    .toList();
                        } else {
                            throw new UnsupportedOperationException("NYI: compute linked variables, merge them");
                        }
                    }
                    return res;
                } else {
                    // no access to the code, directly link to object
                    return additionalLinkingConsumerMethodReference(context, methodReference);
                }
            } else {
                throw new UnsupportedOperationException("Method reference evaluated into " + parameterValue.getClass());
            }
        } else if ((instance = parameterExpression.asInstanceOf(Instance.class)) != null) {
            MethodTypeParameterMap sam = instance.returnType()
                    .findSingleAbstractMethodOfInterface(context.getAnalyserContext());
            methodInfo = sam.methodInspection.getMethodInfo();
            nestedType = sam.methodInspection.getMethodInfo().typeInfo;
        } else {
            methodInfo = null;
            nestedType = null;
        }
        if (methodInfo != null) {
            TypeInspection returnTypeInspection = context.getAnalyserContext()
                    .getTypeInspection(parameterExpression.returnType().typeInfo);
            MethodInspection sam = returnTypeInspection.getSingleAbstractMethod();
            assert sam != null;
            if (sam.getMethodInfo().isVoid()) {
                assert nestedType != null;
                return additionalLinkingConsumer(context.evaluationContext(), methodInfo, nestedType);
            }
        }
        // not yet implemented
        return List.of();
    }

    private static List<LinkedVariables> additionalLinkingConsumerMethodReference(EvaluationResult context,
                                                                                  MethodReference methodReference) {
        DV immutable = context.getProperty(methodReference.scope, Property.IMMUTABLE);
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) return List.of();
        LinkedVariables allLinkedVariablesOfScope = methodReference.scope.evaluate(context, ForwardEvaluationInfo.DEFAULT).linkedVariablesOfExpression();
        if (allLinkedVariablesOfScope.isEmpty()) return List.of();
        LinkedVariables linkedVariablesOfScope = allLinkedVariablesOfScope
                .remove(v -> !context.evaluationContext().acceptForVariableAccessReport(v, null));
        if (linkedVariablesOfScope.isEmpty()) return List.of();

        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(methodReference.methodInfo);
        DV modified = methodAnalysis.getProperty(Property.MODIFIED_METHOD);
        if (DV.FALSE_DV.equals(modified)) return List.of();

        MethodInspection methodInspection = context.getAnalyserContext().getMethodInspection(methodReference.methodInfo);
        List<LinkedVariables> result = new ArrayList<>(methodInspection.getParameters().size());
        for (ParameterInfo pi : methodInspection.getParameters()) {
            ParameterAnalysis parameterAnalysis = context.getAnalyserContext().getParameterAnalysis(pi);
            DV independent = parameterAnalysis.getProperty(Property.INDEPENDENT);
            if (DEPENDENT_DV.equals(independent)) result.add(linkedVariablesOfScope);
            else if (INDEPENDENT_HC_DV.equals(independent)) {
                // modify to :4
                LinkedVariables lv = linkedVariablesOfScope.maximum(LINK_COMMON_HC);
                result.add(lv);
            }
        }
        return result;
    }

    public static List<LinkedVariables> additionalLinkingConsumer(EvaluationContext evaluationContext,
                                                                  MethodInfo concreteMethod,
                                                                  TypeInfo nestedType) {

        MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(concreteMethod);
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        if (lastStatement == null) {
            return List.of();
        }
        MethodInspection methodInspection = evaluationContext.getAnalyserContext().getMethodInspection(concreteMethod);
        List<LinkedVariables> result = new ArrayList<>(methodInspection.getParameters().size());
        for (ParameterInfo pi : methodInspection.getParameters()) {
            VariableInfo vi = lastStatement.getLatestVariableInfo(pi.fullyQualifiedName);
            LinkedVariables lv = vi.getLinkedVariables().remove(v ->
                    !evaluationContext.acceptForVariableAccessReport(v, nestedType));
            result.add(lv);
        }
        return result;
    }

    public record FromParameters(EvaluationResult intoObject, EvaluationResult intoResult) {
    }

    /*
    Add all necessary links from parameters into scope, and in-between parameters
     */
    public FromParameters fromParametersIntoObject(ParameterizedType objectPt,
                                                   ParameterizedType resultPt,
                                                   List<Expression> parameterExpressions,
                                                   List<EvaluationResult> parameterResults,
                                                   boolean fromParametersIntoObject,
                                                   boolean addLinksBetweenParameters) {
        MethodInspection methodInspection = context.getAnalyserContext().getMethodInspection(methodInfo);
        EvaluationResultImpl.Builder intoObjectBuilder = new EvaluationResultImpl.Builder(context)
                .setLinkedVariablesOfExpression(LinkedVariables.EMPTY);
        EvaluationResultImpl.Builder intoResultBuilder = resultPt == null || resultPt.isVoid() ? null
                : new EvaluationResultImpl.Builder(context).setLinkedVariablesOfExpression(LinkedVariables.EMPTY);

        if (!methodInspection.getParameters().isEmpty()) {
            List<LinkedVariables> parameterLv = computeLinkedVariablesOfParameters(parameterResults);

            if (fromParametersIntoObject) {
                // links between object and parameters
                for (ParameterAnalysis parameterAnalysis : methodAnalysis.getParameterAnalyses()) {
                    DV formalParameterIndependent = parameterAnalysis.getProperty(Property.INDEPENDENT);
                    if (!INDEPENDENT_DV.equals(formalParameterIndependent)) {
                        ParameterInfo pi = parameterAnalysis.getParameterInfo();
                        ParameterizedType parameterType = pi.parameterizedType;
                        LinkedVariables parameterLvs = parameterLv.get(pi.index);
                        boolean inResult = intoResultBuilder != null
                                           && parameterExpressions.get(pi.index).isInstanceOf(MethodReference.class);
                        ParameterizedType pt = inResult ? resultPt : objectPt;
                        LinkedVariables lv = computeIndependent.linkedVariables(parameterType, parameterLvs,
                                formalParameterIndependent, parameterAnalysis.getHiddenContentSelector(), pt);
                        EvaluationResultImpl.Builder builder = inResult ? intoResultBuilder : intoObjectBuilder;
                        builder.mergeLinkedVariablesOfExpression(lv);
                    }
                }
            }

            if (addLinksBetweenParameters) {
                linksBetweenParameters(intoObjectBuilder, methodInfo, parameterLv);
            }
        }
        return new FromParameters(intoObjectBuilder.build(), intoResultBuilder == null ? null :
                intoResultBuilder.build());
    }

    public void linksBetweenParameters(EvaluationResultImpl.Builder builder,
                                       MethodInfo concreteMethod,
                                       List<LinkedVariables> parameterLvs) {
        Map<ParameterInfo, LinkedVariables> crossLinks = concreteMethod.crossLinks(context.getAnalyserContext());
        if (crossLinks.isEmpty()) return;
        crossLinks.forEach((pi, lv) -> lv.stream().forEach(e -> {
            ParameterInfo target = (ParameterInfo) e.getKey();
            boolean sourceIsVarArgs = pi.parameterInspection.get().isVarArgs();
            assert !sourceIsVarArgs : "Varargs must always be a target";
            boolean targetIsVarArgs = target.parameterInspection.get().isVarArgs();
            LV level = e.getValue();
            LinkedVariables sourceLvs = parameterLvs.get(pi.index);
            LinkedVariables targetLvs = parameterLvs.get(target.index);
            if (!targetLvs.isEmpty()) {
                tryLinkBetweenParameters(builder, target.index, targetIsVarArgs, target.parameterizedType,
                        targetLvs, level, pi.parameterizedType, sourceLvs, parameterLvs);
            }

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
                                          LinkedVariables targetLinkedVariables,
                                          LV level,
                                          ParameterizedType sourceType,
                                          LinkedVariables sourceLinkedVariables,
                                          List<LinkedVariables> parameterLvs) {
        LinkedVariables mergedLvs;
        HiddenContentSelector hcs = level.isCommonHC() ? level.mine() : CS_NONE;
        DV independentDv = level.isCommonHC() ? INDEPENDENT_HC_DV : DEPENDENT_DV;
        if (targetIsVarArgs) {
            mergedLvs = LinkedVariables.EMPTY;
            ParameterizedType targetTypeCorrected = targetType.copyWithOneFewerArrays();
            for (int i = targetIndex; i < parameterLvs.size(); i++) {
                LinkedVariables lvs = parameterLvs.get(i);
                LinkedVariables lv = computeIndependent.linkedVariables(targetTypeCorrected, lvs, independentDv, hcs, sourceType);
                mergedLvs = mergedLvs.merge(lv);
            }
        } else {
            mergedLvs = computeIndependent.linkedVariables(targetType, targetLinkedVariables, independentDv, hcs, sourceType);
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
                independent, methodAnalysis.getHiddenContentSelector(), concreteReturnType);
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
