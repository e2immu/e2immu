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

import com.github.javaparser.Position;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.UnevaluatedLambdaExpression;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.Logger;
import org.e2immu.analyser.util.StringUtil;

import java.util.*;

import static org.e2immu.analyser.util.Logger.LogTarget.METHOD_CALL;
import static org.e2immu.analyser.util.Logger.LogTarget.RESOLVE;
import static org.e2immu.analyser.util.Logger.log;

public class ParseMethodCallExpr {
    public static Expression parse(ExpressionContext expressionContext, MethodCallExpr methodCallExpr, MethodTypeParameterMap singleAbstractMethod) {
        log(Logger.LogTarget.METHOD_CALL, "Start parsing method call {}, method name {}, single abstract {}", methodCallExpr,
                methodCallExpr.getNameAsString(), singleAbstractMethod);

        Expression object = methodCallExpr.getScope().map(expressionContext::parseExpression).orElse(null);
        // depending on the object, we'll need to find the method somewhere
        ParameterizedType typeOfObject;
        if (object == null) {
            typeOfObject = new ParameterizedType(expressionContext.enclosingType, 0);
        } else {
            typeOfObject = object.returnType();
        }
        Map<NamedType, ParameterizedType> typeMap = typeOfObject.initialTypeParameterMap();
        String methodName = methodCallExpr.getName().asString();
        List<TypeContext.MethodCandidate> methodCandidates = new ArrayList<>();
        expressionContext.typeContext.recursivelyResolveOverloadedMethods(typeOfObject, methodName, methodCallExpr.getArguments().size(), typeMap, methodCandidates);
        if (methodCandidates.isEmpty()) {
            throw new UnsupportedOperationException("No method candidates for method " + methodName);
        }
        List<Expression> newParameterExpressions = new ArrayList<>();
        Map<NamedType, ParameterizedType> mapExpansion = new HashMap<>();

        MethodTypeParameterMap method = evaluateCall(expressionContext, methodCandidates,
                methodCallExpr.getArguments(), newParameterExpressions, singleAbstractMethod, mapExpansion,
                "method " + methodName,
                typeOfObject,
                methodCallExpr.getBegin().orElseThrow());
        log(METHOD_CALL, "End parsing method call {}, return types of method parameters [{}], concrete type {}, mapExpansion {}",
                methodCallExpr, StringUtil.join(newParameterExpressions, Expression::returnType),
                method.getConcreteReturnType().detailedString(), mapExpansion);

        if (method.methodInfo.typeInfo == expressionContext.enclosingType) {
            // only when we're in the same type shall we add method dependencies
            log(RESOLVE, "Add method dependency to {}", method.methodInfo.name);
            expressionContext.dependenciesOnOtherMethodsAndFields.add(method.methodInfo);
        }
        return new MethodCall(object, mapExpansion.isEmpty() ? method : method.expand(mapExpansion), newParameterExpressions);
    }

    static MethodTypeParameterMap evaluateCall(ExpressionContext expressionContext,
                                               List<TypeContext.MethodCandidate> methodCandidates,
                                               List<com.github.javaparser.ast.expr.Expression> expressions,
                                               List<Expression> newParameterExpressions,
                                               MethodTypeParameterMap singleAbstractMethod,
                                               Map<NamedType, ParameterizedType> mapExpansion,
                                               String methodNameForErrorReporting,
                                               ParameterizedType startingPointForErrorReporting,
                                               Position positionForErrorReporting) {

        List<Expression> parameterExpressions = new ArrayList<>();
        for (com.github.javaparser.ast.expr.Expression expr : expressions) {
            parameterExpressions.add(expressionContext.parseExpression(expr));
        }

        // then find out which method
        log(METHOD_CALL, "parameters are of type [{}]", StringUtil.join(parameterExpressions, e -> e.getClass().getSimpleName()));
        MethodTypeParameterMap method = expressionContext.typeContext.resolveMethod(methodCandidates, parameterExpressions,
                methodNameForErrorReporting, startingPointForErrorReporting, positionForErrorReporting);
        log(METHOD_CALL, "resolved method is {}, return type {}", method.methodInfo.fullyQualifiedName(), method.getConcreteReturnType());

        // now parse the lambda's with our new info

        log(METHOD_CALL, "Reevaluating parameter expressions, single abstract method {}", singleAbstractMethod == null ? "-" : singleAbstractMethod.methodInfo.distinguishingName());
        int i = 0;
        for (Expression e : parameterExpressions) {
            if (e instanceof UnevaluatedLambdaExpression) {
                MethodTypeParameterMap abstractInterfaceMethod = determineAbstractInterfaceMethod(expressionContext.typeContext, method, i, singleAbstractMethod);
                if (abstractInterfaceMethod != null) {
                    Expression reParsed = expressionContext.parseExpression(expressions.get(i), abstractInterfaceMethod);
                    newParameterExpressions.add(reParsed);
                } else {
                    throw new UnsupportedOperationException();
                }
            } else newParameterExpressions.add(e);
            i++;
        }

        // fill in the map expansion
        i = 0;
        List<ParameterInfo> formalParameters = method.methodInfo.methodInspection.get().parameters;
        for (Expression expression : newParameterExpressions) {
            log(METHOD_CALL, "Examine parameter {}", i);
            ParameterizedType concreteParameterType = expression.returnType();
            Map<NamedType, ParameterizedType> translated = formalParameters.get(i).parameterizedType.translateMap(concreteParameterType, expressionContext.typeContext);
            translated.forEach((k, v) -> {
                if (!mapExpansion.containsKey(k)) mapExpansion.put(k, v);
            });
            i++;
            if (i >= formalParameters.size()) break; // varargs... we have more than there are
        }
        return method;
    }

    /*
    This method is about aligning the type parameters of the functional interface of the current method chain (the returnType)
    with the functional interface of the current method parameter.
    The example situation is the following (in DependencyGraph.java)

        Map.Entry<T, Node<T>> toRemove = map.entrySet().stream().min(Comparator.comparingInt(e -> e.getValue().dependsOn.size())).orElseThrow();

    The result of stream() is a Stream<Map.Entry<T, Node<T>>
    min() operates on any stream, and takes as argument a Comparator<T> (that's a different T)
    the comparingInt method has the signature: static <U> Comparator<U> comparingInt(ToIntFunction<? super U> keyExtractor);
    Its single abstract method in ToIntFunction<R> is int applyInt(R r)

    This method here ensures that the U in comparingInt is linked to the result of Stream, so that e -> e.getValue can be evaluated on the Map.Entry type
     */
    private static MethodTypeParameterMap determineAbstractInterfaceMethod(TypeContext typeContext,
                                                                           MethodTypeParameterMap method,
                                                                           int p,
                                                                           MethodTypeParameterMap singleAbstractMethod) {
        MethodTypeParameterMap abstractInterfaceMethod = method.getConcreteTypeOfParameter(p).findSingleAbstractMethodOfInterface(typeContext);
        log(METHOD_CALL, "Abstract interface method of parameter {} of method {} is {}", p, method.methodInfo.fullyQualifiedName(), abstractInterfaceMethod);
        // TODO equals should become: isAssignableFrom, in the right order (which one comes left, which one right?) and following the correct type parameter.

        if (abstractInterfaceMethod != null && singleAbstractMethod != null && singleAbstractMethod.methodInfo.typeInfo.equals(method.methodInfo.typeInfo)) {
            Map<NamedType, ParameterizedType> links = new HashMap<>();
            int pos = 0;
            for (TypeParameter key : singleAbstractMethod.methodInfo.typeInfo.typeInspection.get().typeParameters) {
                NamedType abstractTypeInCandidate = method.methodInfo.methodInspection.get().returnType.parameters.get(pos).typeParameter;
                ParameterizedType valueOfKey = singleAbstractMethod.applyMap(key);
                links.put(abstractTypeInCandidate, valueOfKey);
                pos++;
            }
            return abstractInterfaceMethod.expand(links);
        }

        return abstractInterfaceMethod;
    }

}
