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
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MethodTypeParameterMap;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.expression.FieldAccess;
import org.e2immu.analyser.model.expression.MethodReference;
import org.e2immu.analyser.model.expression.UnevaluatedLambdaExpression;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.parser.TypeContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
                // this is a constructor we have to create ourselves...
                MethodInfo arrayConstructor = ParseArrayCreationExpr.createArrayCreationConstructor(parameterizedType);
                return new MethodReference(arrayConstructor, parameterizedType);
            }
            methodNameForErrorReporting = "constructor";
            methodCandidates = expressionContext.typeContext.resolveConstructor(parameterizedType, parametersPresented, parameterizedType.initialTypeParameterMap());
        } else {
            methodCandidates = new ArrayList<>();
            methodNameForErrorReporting = "method " + methodName;
            expressionContext.typeContext.recursivelyResolveOverloadedMethods(parameterizedType,
                    methodName, parametersPresented, parameterizedType.initialTypeParameterMap(), methodCandidates);
        }
        MethodTypeParameterMap method = expressionContext.typeContext.resolveMethod(methodCandidates, null,
                methodNameForErrorReporting, parameterizedType, methodReferenceExpr.getBegin().orElseThrow());

        List<ParameterizedType> types = new ArrayList<>();
        types.add(parameterizedType);
        method.methodInfo.methodInspection.get().parameters.stream().map(p -> p.parameterizedType).forEach(types::add);
        ParameterizedType functionalType = singleAbstractMethod.inferFunctionalType(types, method.getConcreteReturnType());
        return new MethodReference(method.methodInfo, functionalType);
    }

    private static Expression unevaluated(ExpressionContext expressionContext, MethodReferenceExpr methodReferenceExpr) {
        // TODO we can try to see how many constructors there are, or how many methods... if so, we can be pretty specific... or not.
        return new UnevaluatedLambdaExpression(-1, null);
    }
}
