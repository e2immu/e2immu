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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
            if (expression.isInstanceOf(VariableExpression.class) || value.isInstanceOf(ExpandedVariable.class)) {
                lv = expression.linkedVariables(context);
            } else if (expression.returnType().isFunctionalInterface()) {
                lv = additionalLinkingFunctionalInterface(context, expression, parameterValues.get(i));
            } else {
                lv = value.linkedVariables(context);
            }
            result.add(lv.maximum(LinkedVariables.LINK_DEPENDENT));
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
                LinkedVariables lv = linkedVariablesOfScope.maximum(LinkedVariables.LINK_COMMON_HC);
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

    public static LinkedVariables fromParametersIntoObject(EvaluationResult context,
                                                           MethodAnalysis methodAnalysis,
                                                           LinkedVariables scopeLv,
                                                           ParameterizedType scopePt,
                                                           List<Expression> parameterExpressions) {
        ComputeIndependent computeIndependent = new ComputeIndependent(context.getAnalyserContext(), context.getCurrentType());
        DV scopeImmutable = computeIndependent.typeImmutable(scopePt);
        Map<Variable, DV> newLvMap = new HashMap<>(scopeLv.variables());

        for (ParameterAnalysis parameterAnalysis : methodAnalysis.getParameterAnalyses()) {
            DV paramIndependent = parameterAnalysis.getProperty(Property.INDEPENDENT);
            if (!MultiLevel.INDEPENDENT_DV.equals(paramIndependent)) {

                int index = parameterAnalysis.getParameterInfo().index;
                Expression parameterExpression = parameterExpressions.get(index);
                ParameterizedType concreteParameterType = parameterExpression.returnType();
                DV linkLevel = LinkedVariables.fromIndependentToLinkedVariableLevel(paramIndependent);
                DV dv = computeIndependent.typesAtLinkLevel(linkLevel, scopePt, scopeImmutable, concreteParameterType);
                if (!LinkedVariables.LINK_INDEPENDENT.equals(dv)) {
                    LinkedVariables linkedVariables = parameterExpression.linkedVariables(context);
                    for (Map.Entry<Variable, DV> e : linkedVariables) {
                        newLvMap.merge(e.getKey(), dv, DV::max); // we follow the links
                    }
                }
            }
        }
        return LinkedVariables.of(newLvMap);
    }
}
