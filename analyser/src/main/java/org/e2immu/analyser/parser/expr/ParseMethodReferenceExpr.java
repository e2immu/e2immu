/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.parser.expr;

import com.github.javaparser.ast.expr.MethodReferenceExpr;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.FieldAccess;
import org.e2immu.analyser.model.expression.MethodReference;
import org.e2immu.analyser.model.expression.UnevaluatedLambdaExpression;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.parser.TypeContext;

import java.util.ArrayList;
import java.util.List;

import static org.e2immu.analyser.util.Logger.LogTarget.METHOD_CALL;
import static org.e2immu.analyser.util.Logger.log;

public class ParseMethodReferenceExpr {
    public static Expression parse(ExpressionContext expressionContext, MethodReferenceExpr methodReferenceExpr, MethodTypeParameterMap singleAbstractMethod) {
        if (singleAbstractMethod == null) {
            return unevaluated(expressionContext, methodReferenceExpr);
        }
        Expression scope = expressionContext.parseExpression(methodReferenceExpr.getScope());
        ParameterizedType parameterizedType = scope.returnType();
        expressionContext.dependenciesOnOtherTypes.addAll(parameterizedType.typeInfoSet());
        String methodName = methodReferenceExpr.getIdentifier();
        boolean constructor = "new".equals(methodName);

        // the following examples say that you should look for a method with identical number of parameters:
        // e.g. Function<T, R> has R apply(T t), and we present C::new (where C has a constructor C(String s) { .. }
        // e.g. Consumer<T> has void accept(T t), and we present System.out::println

        // but this example says you need to subtract:
        // e.g. Function<T, R> has R apply(T t), and we present Object::toString

        // the difference is that there is an object scope in the 3rd example, whereas there are none in the first 2
        boolean subTractBecauseOfScope = !constructor && !((scope instanceof FieldAccess && ((FieldAccess) scope).variable.isStatic()));
        int parametersPresented = singleAbstractMethod.methodInfo.methodInspection.get().parameters.size() - (subTractBecauseOfScope ? 1 : 0);
        List<TypeContext.MethodCandidate> methodCandidates;
        String methodNameForErrorReporting;
        if (constructor) {
            if (parameterizedType.arrays > 0) {
                return arrayConstruction(expressionContext, parameterizedType);
            }
            methodNameForErrorReporting = "constructor";
            methodCandidates = expressionContext.typeContext.resolveConstructor(parameterizedType, parametersPresented, parameterizedType.initialTypeParameterMap());
        } else {
            methodCandidates = new ArrayList<>();
            methodNameForErrorReporting = "method " + methodName;
            expressionContext.typeContext.recursivelyResolveOverloadedMethods(parameterizedType,
                    methodName, parametersPresented, parameterizedType.initialTypeParameterMap(), methodCandidates);
        }
        if (methodCandidates.isEmpty()) {
            throw new UnsupportedOperationException("Cannot find a candidate for " + methodNameForErrorReporting + " at " + methodReferenceExpr.getBegin());
        }
        if (methodCandidates.size() > 1) {
            log(METHOD_CALL, "Have multiple candidates, would need to sort somehow to take the best one");
            // TODO doesn't happen that frequently...
        }
        MethodTypeParameterMap method = methodCandidates.get(0).method;
        List<ParameterizedType> types = new ArrayList<>();
        types.add(parameterizedType);
        method.methodInfo.methodInspection.get().parameters.stream().map(p -> p.parameterizedType).forEach(types::add);
        ParameterizedType functionalType = singleAbstractMethod.inferFunctionalType(types, method.getConcreteReturnType());
        return new MethodReference(method.methodInfo, functionalType);
    }

    private static Expression unevaluated(ExpressionContext expressionContext, MethodReferenceExpr methodReferenceExpr) {
        Expression scope = expressionContext.parseExpression(methodReferenceExpr.getScope());
        ParameterizedType parameterizedType = scope.returnType();
        String methodName = methodReferenceExpr.getIdentifier();
        boolean constructor = "new".equals(methodName);

        List<TypeContext.MethodCandidate> methodCandidates;
        if (constructor) {
            if (parameterizedType.arrays > 0) {
                return arrayConstruction(expressionContext, parameterizedType);
            }
            methodCandidates = expressionContext.typeContext.resolveConstructor(parameterizedType, TypeContext.IGNORE_PARAMETER_NUMBERS, parameterizedType.initialTypeParameterMap());
        } else {
            methodCandidates = new ArrayList<>();
            expressionContext.typeContext.recursivelyResolveOverloadedMethods(parameterizedType,
                    methodName, TypeContext.IGNORE_PARAMETER_NUMBERS, parameterizedType.initialTypeParameterMap(), methodCandidates);
        }
        if (methodCandidates.isEmpty()) {
            throw new UnsupportedOperationException("Cannot find a candidate for " + (constructor ? "constructor" : methodName) + " at " + methodReferenceExpr.getBegin());
        }
        if (methodCandidates.size() == 1) {
            TypeContext.MethodCandidate methodCandidate = methodCandidates.get(0);
            log(METHOD_CALL, "Have exactly one method reference candidate, this can work: {}", methodCandidate.method.methodInfo.distinguishingName());
            MethodInspection methodInspection = methodCandidate.method.methodInfo.methodInspection.get();
            boolean addOne = !methodCandidate.method.methodInfo.isConstructor && !methodCandidate.method.methodInfo.isStatic;
            int numberOfParameters = methodInspection.parameters.size() + (addOne ? 1 : 0);
            return new UnevaluatedLambdaExpression(numberOfParameters, !methodInspection.returnType.isVoid());
        }
        return new UnevaluatedLambdaExpression(-1, null);
    }

    private static MethodReference arrayConstruction(ExpressionContext expressionContext, ParameterizedType parameterizedType) {
        MethodInfo arrayConstructor = ParseArrayCreationExpr.createArrayCreationConstructor(parameterizedType);
        TypeInfo intFunction = expressionContext.typeContext.typeStore.get("java.util.function.IntFunction");
        if (intFunction == null) throw new UnsupportedOperationException("? need IntFunction");
        ParameterizedType intFunctionPt = new ParameterizedType(intFunction, List.of(parameterizedType));
        return new MethodReference(arrayConstructor, intFunctionPt);
    }
}
