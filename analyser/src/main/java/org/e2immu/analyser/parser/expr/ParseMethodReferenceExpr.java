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
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.parser.TypeContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.e2immu.analyser.util.Logger.LogTarget.METHOD_CALL;
import static org.e2immu.analyser.util.Logger.log;

public class ParseMethodReferenceExpr {

    public static Expression parse(ExpressionContext expressionContext, MethodReferenceExpr methodReferenceExpr, MethodTypeParameterMap singleAbstractMethod) {
        if (singleAbstractMethod == null) {
            return unevaluated(expressionContext, methodReferenceExpr);
        }
        Expression scope = expressionContext.parseExpression(methodReferenceExpr.getScope());
        boolean scopeIsAType = scopeIsAType(scope);
        ParameterizedType parameterizedType = scope.returnType();
        expressionContext.dependenciesOnOtherTypes.addAll(parameterizedType.typeInfoSet());
        String methodName = methodReferenceExpr.getIdentifier();
        boolean constructor = "new".equals(methodName);

        int parametersPresented = singleAbstractMethod.methodInfo.methodInspection.get().parameters.size();
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

            // the following examples say that you should look for a method with identical number of parameters:
            // e.g. Function<T, R> has R apply(T t), and we present C::new (where C has a constructor C(String s) { .. }
            // e.g. Consumer<T> has void accept(T t), and we present System.out::println
            // e.g. Functional interface LogMethod:  void log(LogTarget logTarget, String message, Object... objects), we present Logger::logViaLogBackClassic

            // but this example says you need to subtract:
            // e.g. Function<T, R> has R apply(T t), and we present Object::toString (the scope is the first argument)
            expressionContext.typeContext.recursivelyResolveOverloadedMethods(parameterizedType,
                    methodName, parametersPresented, scopeIsAType, parameterizedType.initialTypeParameterMap(), methodCandidates);
        }
        if (methodCandidates.isEmpty()) {
            throw new UnsupportedOperationException("Cannot find a candidate for " + methodNameForErrorReporting + " at " + methodReferenceExpr.getBegin());
        }
        if (methodCandidates.size() > 1) {
            log(METHOD_CALL, "Have multiple candidates, would need to sort somehow to take the best one");
            methodCandidates.removeIf(mc -> {
                // check types of parameters in SAM
                // example here is in Variable: there is a detailedString(), and a static detailedString(Set<>)
                for (int i = 0; i < singleAbstractMethod.methodInfo.methodInspection.get().parameters.size(); i++) {
                    ParameterizedType concreteType = singleAbstractMethod.getConcreteTypeOfParameter(i);
                    ParameterizedType typeOfMethodCandidate;
                    int param = scopeIsAType && !constructor && !mc.method.methodInfo.isStatic ? i - 1 : i;
                    if (param == -1) {
                        typeOfMethodCandidate = mc.method.methodInfo.typeInfo.asParameterizedType();
                    } else {
                        typeOfMethodCandidate = mc.method.methodInfo.methodInspection.get().parameters.get(i).parameterizedType;
                    }
                    if (!typeOfMethodCandidate.isAssignableFrom(concreteType)) return true;
                }
                return false;
            });
            if (methodCandidates.size() > 1) {
                TypeContext.MethodCandidate mc0 = methodCandidates.get(0);
                Set<MethodInfo> overloads = mc0.method.methodInfo.typeInfo.overloads(mc0.method.methodInfo, expressionContext.typeContext);
                for (TypeContext.MethodCandidate mcN : methodCandidates.subList(1, methodCandidates.size())) {
                    if (!overloads.contains(mcN.method.methodInfo) && mcN.method.methodInfo != mc0.method.methodInfo) {
                        throw new UnsupportedOperationException("Not all candidates are overloads of the 1st one! No unique " +
                                methodNameForErrorReporting + " found in known at position " + methodReferenceExpr.getBegin());
                    }
                }
            }
        }
        MethodTypeParameterMap method = methodCandidates.get(0).method;
        List<ParameterizedType> types = new ArrayList<>();
        types.add(parameterizedType);
        method.methodInfo.methodInspection.get().parameters.stream().map(p -> p.parameterizedType).forEach(types::add);
        ParameterizedType functionalType = singleAbstractMethod.inferFunctionalType(types, method.getConcreteReturnType());
        log(METHOD_CALL, "End parsing method reference {}, found {}", methodNameForErrorReporting, method.methodInfo.distinguishingName());
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
                    methodName, TypeContext.IGNORE_PARAMETER_NUMBERS, false, parameterizedType.initialTypeParameterMap(), methodCandidates);
        }
        if (methodCandidates.isEmpty()) {
            throw new UnsupportedOperationException("Cannot find a candidate for " + (constructor ? "constructor" : methodName) + " at " + methodReferenceExpr.getBegin());
        }
        Set<Integer> numberOfParameters = new HashSet<>();
        for (TypeContext.MethodCandidate methodCandidate : methodCandidates) {
            log(METHOD_CALL, "Have exactly one method reference candidate, this can work: {}", methodCandidate.method.methodInfo.distinguishingName());
            MethodInspection methodInspection = methodCandidate.method.methodInfo.methodInspection.get();
            boolean scopeIsType = scopeIsAType(scope);
            boolean addOne = scopeIsType && !methodCandidate.method.methodInfo.isConstructor && !methodCandidate.method.methodInfo.isStatic; // && !methodInspection.returnType.isPrimitive();
            int n = methodInspection.parameters.size() + (addOne ? 1 : 0);
            if (!numberOfParameters.add(n)) {
                // throw new UnsupportedOperationException("Multiple candidates with the same amount of parameters");
            }
        }
        return new UnevaluatedLambdaExpression(numberOfParameters, null);
    }

    private static MethodReference arrayConstruction(ExpressionContext expressionContext, ParameterizedType parameterizedType) {
        MethodInfo arrayConstructor = ParseArrayCreationExpr.createArrayCreationConstructor(parameterizedType);
        TypeInfo intFunction = expressionContext.typeContext.typeStore.get("java.util.function.IntFunction");
        if (intFunction == null) throw new UnsupportedOperationException("? need IntFunction");
        ParameterizedType intFunctionPt = new ParameterizedType(intFunction, List.of(parameterizedType));
        return new MethodReference(arrayConstructor, intFunctionPt);
    }

    public static boolean scopeIsAType(Expression scope) {
        return !(scope instanceof VariableExpression) && !(scope instanceof FieldAccess);
    }
}
