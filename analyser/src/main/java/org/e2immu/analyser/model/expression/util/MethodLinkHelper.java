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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
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
        int i = 0;
        List<LinkedVariables> result = new ArrayList<>(parameterResults.size());
        for (EvaluationResult evaluationResult : parameterResults) {
            Expression value = evaluationResult.value();
            LinkedVariables lv;
            if (value.returnType().isFunctionalInterface()) {
                lv = additionalLinkingFunctionalInterface(context, value, parameterResults.get(i));
            } else {
                lv = evaluationResult.linkedVariablesOfExpression();
            }
            result.add(lv.maximum(LINK_DEPENDENT));
            i++;
        }
        return result;
    }

    private static LinkedVariables additionalLinkingFunctionalInterface(EvaluationResult context,
                                                                        Expression parameterExpression,
                                                                        EvaluationResult parameterResult) {
        List<LinkedVariables> list = additionalLinkingFunctionalInterface2(context, parameterExpression, parameterResult);
        return list.stream().reduce(LinkedVariables.EMPTY, LinkedVariables::merge);
    }

    /*
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

    private static List<LinkedVariables> additionalLinkingConsumer(EvaluationContext evaluationContext,
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

    /*
    Add all necessary links from parameters into scope, and in-between parameters
     */
    public EvaluationResult fromParametersIntoObject(EvaluationResult objectResult,
                                                     List<EvaluationResult> parameterResults,
                                                     boolean fromParametersIntoObject,
                                                     boolean addLinksBetweenParameters) {
        MethodInspection methodInspection = context.getAnalyserContext().getMethodInspection(methodInfo);
        EvaluationResultImpl.Builder builder = new EvaluationResultImpl.Builder(context);
        LinkedVariables linkedVariablesOfObject = objectResult.linkedVariablesOfExpression();

        if (methodInspection.getParameters().isEmpty()) {
            return builder.build(); // nothing more we can do here
        }

        ParameterizedType objectPt = objectResult.getExpression().returnType();

        List<LinkedVariables> parameterLv = computeLinkedVariablesOfParameters(parameterResults);

        if (fromParametersIntoObject) {
            DV scopeImmutable = computeIndependent.typeImmutable(objectPt);
            // store the results by doing builder.link()...

            // links between object and parameters
            for (ParameterAnalysis parameterAnalysis : methodAnalysis.getParameterAnalyses()) {
                DV formalParameterIndependent = parameterAnalysis.getProperty(Property.INDEPENDENT);
                if (!INDEPENDENT_DV.equals(formalParameterIndependent)) {

                    int index = parameterAnalysis.getParameterInfo().index;
                    EvaluationResult parameterResult = parameterResults.get(index);
                    ParameterizedType concreteParameterType = parameterResult.value().returnType();
                    LV linkLevel = LinkedVariables.fromIndependentToLinkedVariableLevel(formalParameterIndependent);
                    DV parameterIndependent = computeIndependent.typesAtLinkLevel(linkLevel, objectPt, scopeImmutable, concreteParameterType);
                    LV parameterIndependentLevel = LinkedVariables.fromIndependentToLinkedVariableLevel(parameterIndependent);

                    if (!INDEPENDENT_DV.equals(parameterIndependent)) {
                        assert parameterIndependentLevel.isDelayed()
                               || LINK_DEPENDENT.equals(parameterIndependentLevel)
                               || LINK_COMMON_HC.equals(parameterIndependentLevel);
                        LinkedVariables linkedVariablesOfParameter = parameterResult.linkedVariablesOfExpression();

                        for (Map.Entry<Variable, LV> eFrom : linkedVariablesOfObject) {
                            for (Map.Entry<Variable, LV> eTo : linkedVariablesOfParameter) {
                                LV level = eFrom.getValue().max(eTo.getValue()).max(parameterIndependentLevel);
                                builder.link(eFrom.getKey(), eTo.getKey(), level);
                            }
                        }
                    }
                }
            }
        }

        if (addLinksBetweenParameters) {
            linksBetweenParameters(builder, methodInfo, parameterResults, parameterLv);
        }
        builder.setLinkedVariablesOfExpression(linkedVariablesOfObject);
        return builder.build();
    }

    public void linksBetweenParameters(EvaluationResultImpl.Builder builder,
                                       MethodInfo concreteMethod,
                                       List<EvaluationResult> parameterResults,
                                       List<LinkedVariables> parameterLv) {
        // key is dependent on values, but only if all of them are variable expressions
        Map<ParameterInfo, LinkedVariables> crossLinks = concreteMethod.crossLinks(context.getAnalyserContext());
        if (crossLinks.isEmpty()) return;
        crossLinks.forEach((pi, lv) -> lv.stream().forEach(e -> {
            ParameterInfo target = (ParameterInfo) e.getKey();
            boolean targetIsVarArgs = target.parameterInspection.get().isVarArgs();
            LV level = e.getValue();
            EvaluationResult targetResult = parameterResults.get(target.index);
            Expression targetValue = targetResult.value();
            /*
            FIXME solve when we've tested more
            Variable targetVariable = bestTargetVariable(targetExpression, targetValue);
            if (targetVariable != null) {
                Expression expression = bestExpression(parameterExpressions.get(pi.index), parameterValues.get(pi.index));
                tryLinkBetweenParameters(builder, context, targetVariable, target.index, targetIsVarArgs, level, expression,
                        parameterExpressions, parameterValues, parameterLv);
            }*/

        }));
    }

    //
    //in order of importance:

    //InlinedMethod priority over Lambda
    //

    private Expression bestExpression(Expression raw, Expression evaluated) {
        if (evaluated.isInstanceOf(IsVariableExpression.class)) return evaluated;
        if (evaluated.isInstanceOf(InlinedMethod.class)) return evaluated;
        MethodReference mr = evaluated.asInstanceOf(MethodReference.class);
        if (mr != null && mr.scope.isInstanceOf(IsVariableExpression.class)) return evaluated;
        return raw;
    }

    private Variable bestTargetVariable(Expression targetExpression, Expression targetValue) {
        IsVariableExpression ive = targetValue.asInstanceOf(IsVariableExpression.class);
        if (ive != null) {
            return ive.variable();
        }
        IsVariableExpression ive2 = targetExpression.asInstanceOf(IsVariableExpression.class);
        if (ive2 != null) {
            return ive2.variable();
        }
        return null;
    }

    //
    //example Independent1_2_1
    //target = ts, index 0, not varargs, linked 4=common_hc to generator, index 1;
    //target ts is modified; values are new String[4] and generator, linked variables are this.ts:2 and generator:2
    //
    private void tryLinkBetweenParameters(EvaluationResultImpl.Builder builder,
                                          EvaluationResult context,
                                          Variable target,
                                          int targetIndex,
                                          boolean targetIsVarArgs,
                                          LV level,
                                          Expression source,
                                          List<Expression> parameterExpressions,
                                          List<Expression> parameterValues,
                                          List<LinkedVariables> linkedVariables) {
        IsVariableExpression vSource = source.asInstanceOf(IsVariableExpression.class);
        if (vSource != null) {
            // Independent1_2, DependentVariables_1
            linksBetweenParametersVarArgs(builder, targetIndex, targetIsVarArgs, level, vSource,
                    parameterExpressions, parameterValues, linkedVariables);
        }
        MethodReference methodReference = source.asInstanceOf(MethodReference.class);
        if (methodReference != null) {
            // Independent1_3
            IsVariableExpression mrSource = methodReference.scope.asInstanceOf(IsVariableExpression.class);
            if (mrSource != null) {
                linksBetweenParametersVarArgs(builder, targetIndex, targetIsVarArgs, level, mrSource,
                        parameterExpressions, parameterValues, linkedVariables);
            }
        }
        InlinedMethod inlinedMethod = source.asInstanceOf(InlinedMethod.class);
        if (inlinedMethod != null) {
            // Independent1_4 TODO written to fit exactly this situation, needs expanding
            // we decide between the first argument of the lambda and the return type
            // first, the return type TODO
            ParameterizedType typeOfHiddenContent = inlinedMethod.returnType().erased();
            ParameterizedType typeOfTarget = target.parameterizedType().erased();
            if (typeOfHiddenContent.equals(typeOfTarget)) {
                Expression srv = context.getAnalyserContext().getMethodAnalysis(inlinedMethod.methodInfo()).getSingleReturnValue();
                List<Variable> vars = srv.variables();
                for (Variable v : vars) {
                    if (v instanceof ParameterInfo piLambda && piLambda.owner != inlinedMethod.methodInfo()) {
                        LV l = srv.isDelayed() ? LV.delay(srv.causesOfDelay()) : level;
                        linksBetweenParametersVarArgs(builder, targetIndex, targetIsVarArgs, l,
                                new VariableExpression(piLambda.identifier, v),
                                parameterExpressions, parameterValues, linkedVariables);
                    }
                }
            }
        }
        // we must have both lambda and inline: lambda to provide the correct delays in LV, and inline to provide the
        // final value. Code is very similar
        Lambda lambda = source.asInstanceOf(Lambda.class);
        if (lambda != null) {
            ParameterizedType typeOfHiddenContent = lambda.concreteReturnType().erased();
            ParameterizedType typeOfTarget = target.parameterizedType().erased();
            if (typeOfHiddenContent.equals(typeOfTarget)) {
                Expression srv = context.getAnalyserContext().getMethodAnalysis(lambda.methodInfo).getSingleReturnValue();
                List<Variable> vars = srv.variables();
                for (Variable v : vars) {
                    if (v instanceof ParameterInfo piLambda && piLambda.owner != lambda.methodInfo) {
                        LV l = srv.isDelayed() ? LV.delay(srv.causesOfDelay()) : level;
                        linksBetweenParametersVarArgs(builder, targetIndex, targetIsVarArgs, l,
                                new VariableExpression(piLambda.identifier, v), parameterExpressions, parameterValues,
                                linkedVariables);
                    }
                }
            }
        }
    }

    private void linksBetweenParametersVarArgs(EvaluationResultImpl.Builder builder,
                                               int targetIndex,
                                               boolean targetIsVarArgs,
                                               LV level,
                                               IsVariableExpression vSource,
                                               List<Expression> parameterExpressions,
                                               List<Expression> parameterValues,
                                               List<LinkedVariables> linkedVariables) {
        if (!LINK_INDEPENDENT.equals(level)) {
            linksBetweenParameters(builder, vSource, targetIndex, level, parameterValues, linkedVariables);
            if (targetIsVarArgs) {
                for (int i = targetIndex + 1; i < parameterExpressions.size(); i++) {
                    linksBetweenParameters(builder, vSource, i, level, parameterValues, linkedVariables);
                }
            }
        }
    }

    private void linksBetweenParameters(EvaluationResultImpl.Builder builder,
                                        IsVariableExpression source,
                                        int targetIndex,
                                        LV level,
                                        List<Expression> parameterValues,
                                        List<LinkedVariables> linkedVariables) {
        LinkedVariables targetLinks = linkedVariables.get(targetIndex);
        Expression parameterValue = parameterValues.get(targetIndex);
        CausesOfDelay delays = parameterValue.causesOfDelay().merge(source.causesOfDelay());
        targetLinks.variables().forEach((v, l) ->
                builder.link(source.variable(), v, delays.isDelayed() ? LV.delay(delays) : level.max(l)));
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

    public LinkedVariables linkedVariables(EvaluationResult objectResult,
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
            return linkedVariablesOfObject
                    .merge(parameterResults.get(0).linkedVariablesOfExpression())
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
