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
import org.e2immu.analyser.analyser.util.ComputeIndependent;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.inspector.MethodTypeParameterMap;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.e2immu.analyser.model.MultiLevel.*;

public class LinkParameters {
    /*
    called by ConstructorCall and MethodCall
     */
    public static List<LinkedVariables> computeLinkedVariablesOfParameters(EvaluationResult context,
                                                                           List<Expression> parameterExpressions,
                                                                           List<Expression> parameterValues) {
        assert parameterExpressions.size() == parameterValues.size();
        int i = 0;
        List<LinkedVariables> result = new ArrayList<>(parameterExpressions.size());
        for (Expression expression : parameterExpressions) {
            Expression value = parameterValues.get(i);
            LinkedVariables lv;
            if (expression instanceof VariableExpression || value instanceof ExpandedVariable) {
                lv = expression.linkedVariables(context);
            } else if (expression.returnType().isFunctionalInterface()) {
                lv = additionalLinkingFunctionalInterface(context, expression, parameterValues.get(i));
            } else {
                lv = value.linkedVariables(context);
            }
            result.add(lv.minimum(LinkedVariables.LINK_DEPENDENT));
            i++;
        }
        return result;
    }

    private static LinkedVariables additionalLinkingFunctionalInterface(EvaluationResult context,
                                                                        Expression parameterExpression,
                                                                        Expression parameterValue) {
        List<LinkedVariables> list = additionalLinkingFunctionalInterface2(context, parameterExpression, parameterValue);
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
                                                                               Expression parameterValue) {
        MethodInfo methodInfo;
        TypeInfo nestedType;
        if (parameterExpression instanceof Lambda lambda) {
            methodInfo = lambda.methodInfo;
            nestedType = lambda.methodInfo.typeInfo;
        } else if (parameterExpression instanceof ConstructorCall cc && cc.anonymousClass() != null) {
            methodInfo = context.getAnalyserContext().getTypeInspection(cc.anonymousClass())
                    .getSingleAbstractMethod().getMethodInfo();
            nestedType = cc.anonymousClass();
        } else if (parameterExpression instanceof MethodReference methodReference) {
            if (parameterValue instanceof MethodReference mr) {
                // do we have access to the code?
                if (methodReference.methodInfo.typeInfo.primaryType().equals(context.getCurrentType().primaryType())) {
                    methodInfo = methodReference.methodInfo;
                    // yes, access to the code!!
                    List<LinkedVariables> res = additionalLinkingConsumer(context.evaluationContext(),
                            methodReference.methodInfo, null);
                    if (mr.scope instanceof TypeExpression) {
                        /*
                         methods which only have a scope, no argument... could be static suppliers, but more likely
                         non-modifying getters. Ignoring for now.
                         */
                        return List.of();
                    }
                    if (!(mr.scope instanceof VariableExpression ve && ve.variable() instanceof This thisVar
                            && thisVar.typeInfo == context.getCurrentType())) {
                        This innerThis = new This(context.getAnalyserContext(), methodInfo.typeInfo);
                        IsVariableExpression ive = mr.scope.asInstanceOf(IsVariableExpression.class);
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
        LinkedVariables allLinkedVariablesOfScope = methodReference.scope.linkedVariables(context);
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
                LinkedVariables lv = linkedVariablesOfScope.minimum(LinkedVariables.LINK_COMMON_HC);
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
    Compute the links from the newly created object (constructor call) or the object (method call) to the
    parameters of the constructor/method.
    */
    public static LinkedVariables linkFromObjectToParameters(EvaluationResult evaluationContext,
                                                             MethodInspection methodInspection,
                                                             List<LinkedVariables> linkedVariables,
                                                             ParameterizedType objectType) {
        DV immutableType = evaluationContext.getAnalyserContext().typeImmutable(objectType);

        LinkedVariables result = LinkedVariables.EMPTY;
        int i = 0;
        for (LinkedVariables sub : linkedVariables) {
            ParameterInfo parameterInfo;
            if (i < methodInspection.getParameters().size()) {
                parameterInfo = methodInspection.getParameters().get(i);
            } else {
                parameterInfo = methodInspection.getParameters().get(methodInspection.getParameters().size() - 1);
                assert parameterInfo.parameterInspection.get().isVarArgs();
            }
            Map<Variable, DV> newLinkMap = new HashMap<>();
            for (Map.Entry<Variable, DV> e : sub) {
                Variable v = e.getKey();
                DV linkLevel = e.getValue();
                if (immutableType.isDelayed()) {
                    newLinkMap.put(v, immutableType);
                } else if (!MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutableType)) {
                    if (linkLevel.isDelayed()) {
                        newLinkMap.put(v, linkLevel);
                    } else {
                        DV newLinkLevel = computeLinkLevelFromParameterAndItsLinkedVariables(evaluationContext,
                                parameterInfo, linkLevel, v.parameterizedType(), objectType, false);
                        if (newLinkLevel.lt(LinkedVariables.LINK_INDEPENDENT)) {
                            newLinkMap.put(v, newLinkLevel);
                        }
                    }
                }
            }
            LinkedVariables lv = LinkedVariables.of(newLinkMap);
            result = result.merge(lv);
            i++;
        }
        return result.minimum(LinkedVariables.LINK_ASSIGNED);
    }

    /*
     Compute the links from the parameters of a method into its object.
     For now not implemented for constructors (we'll need some sort of temporary, reverse link version of IS_HC_OF)

     Object type is important to cut short independence, but cannot be used on self! See e.g. EventuallyE2Immutable_0.
     If we do that, we need to wait for IMMUTABLE of the type before we can compute linking, which is used for independence
     of 'this', ... infinite loop, to be broken.
    */
    public static Map<ParameterInfo, LinkedVariables> fromParameterIntoObject(EvaluationResult context,
                                                                              MethodInspection methodInspection,
                                                                              List<LinkedVariables> linkedVariables,
                                                                              ParameterizedType objectType,
                                                                              ParameterizedType formalObjectType) {
        /*
         formal because we must take the same decision in every iteration. See Lambda_17, where we first find a
         Lambda (in delayed form) and then an InlinedMethod. The return type of the inlined method is different from
         the functional interface method's owner.
         */
        boolean myself = context.evaluationContext().isMyself(formalObjectType).toFalse(Property.IMMUTABLE);
        DV immutableType = myself ? MultiLevel.MUTABLE_DV : context.getAnalyserContext().typeImmutable(formalObjectType);
        Map<ParameterInfo, LinkedVariables> result = new HashMap<>();
        int i = 0;
        for (LinkedVariables sub : linkedVariables) {
            ParameterInfo parameterInfo;
            if (i < methodInspection.getParameters().size()) {
                parameterInfo = methodInspection.getParameters().get(i);
            } else {
                parameterInfo = methodInspection.getParameters().get(methodInspection.getParameters().size() - 1);
                assert parameterInfo.parameterInspection.get().isVarArgs();
            }
            Map<Variable, DV> newLinkMap = new HashMap<>();
            for (Map.Entry<Variable, DV> e : sub) {
                Variable v = e.getKey();
                DV linkLevel = e.getValue().min(LinkedVariables.LINK_ASSIGNED);
                if (immutableType.isDelayed()) {
                    newLinkMap.put(v, immutableType);
                } else if (!MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutableType)) {
                    if (linkLevel.isDelayed()) {
                        newLinkMap.put(v, linkLevel);
                    } else {
                        ParameterizedType ot = myself ? formalObjectType : objectType;
                        DV newLinkLevel = computeLinkLevelFromParameterAndItsLinkedVariables(context,
                                parameterInfo, linkLevel, v.parameterizedType(), ot, true);
                        if (newLinkLevel.lt(LinkedVariables.LINK_INDEPENDENT)) {
                            newLinkMap.put(v, newLinkLevel);
                        }
                    }
                }
            }
            LinkedVariables lv = LinkedVariables.of(newLinkMap);
            result.put(parameterInfo, lv);
            i++;
        }
        return result;
    }

    private static DV computeLinkLevelFromParameterAndItsLinkedVariables(EvaluationResult context,
                                                                         ParameterInfo parameterInfo,
                                                                         DV linkLevel,
                                                                         ParameterizedType variableType,
                                                                         ParameterizedType objectType,
                                                                         boolean fromParameterToObject) {
        assert linkLevel.isDone();

        ParameterAnalysis parameterAnalysis = context.getAnalyserContext().getParameterAnalysis(parameterInfo);
        DV independentOnParameter = parameterAnalysis.getProperty(Property.INDEPENDENT);

        // shortcut: either is at max value, then there is no discussion
        if (INDEPENDENT_DV.equals(independentOnParameter) || linkLevel.equals(LinkedVariables.LINK_INDEPENDENT)) {
            return LinkedVariables.LINK_INDEPENDENT;
        }

        // any delay: wait!
        CausesOfDelay causes = independentOnParameter.causesOfDelay();
        if (causes.isDelayed()) return causes;

        if (DEPENDENT_DV.equals(independentOnParameter)) {
            // if linkLevel is already hidden content, then keep that level
            return LinkedVariables.LINK_DEPENDENT.max(linkLevel);
        }

        // hidden content... use the relation of parameter type with respect to object type (not the return type!)
        if (fromParameterToObject) {
            ComputeIndependent computeIndependent = new ComputeIndependent(context.getAnalyserContext(),
                    context.getCurrentType());
            return computeIndependent.directedLinkLevelOfTwoHCRelatedTypes(variableType, objectType);
        }
        return LinkedVariables.LINK_COMMON_HC;
    }
}
