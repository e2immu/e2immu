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
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.StringUtil;

import java.util.*;

import static org.e2immu.analyser.util.Logger.LogTarget.METHOD_CALL;
import static org.e2immu.analyser.util.Logger.LogTarget.RESOLVE;
import static org.e2immu.analyser.util.Logger.log;

public class ParseMethodCallExpr {
    public static Expression parse(ExpressionContext expressionContext, MethodCallExpr methodCallExpr, MethodTypeParameterMap singleAbstractMethod) {
        log(METHOD_CALL, "Start parsing method call {}, method name {}, single abstract {}", methodCallExpr,
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
        log(METHOD_CALL, "Type map of method call {} is {}", methodCallExpr.getNameAsString(), typeMap);
        String methodName = methodCallExpr.getName().asString();
        List<TypeContext.MethodCandidate> methodCandidates = new ArrayList<>();
        expressionContext.typeContext.recursivelyResolveOverloadedMethods(typeOfObject, methodName, methodCallExpr.getArguments().size(), typeMap, methodCandidates);
        if (methodCandidates.isEmpty()) {
            throw new UnsupportedOperationException("No method candidates for method " + methodName);
        }
        List<Expression> newParameterExpressions = new ArrayList<>();
        Map<NamedType, ParameterizedType> mapExpansion = new HashMap<>();

        MethodTypeParameterMap method = chooseCandidateAndEvaluateCall(expressionContext, methodCandidates,
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

    static MethodTypeParameterMap chooseCandidateAndEvaluateCall(ExpressionContext expressionContext,
                                                                 List<TypeContext.MethodCandidate> methodCandidates,
                                                                 List<com.github.javaparser.ast.expr.Expression> expressions,
                                                                 List<Expression> newParameterExpressions,
                                                                 MethodTypeParameterMap singleAbstractMethod,
                                                                 Map<NamedType, ParameterizedType> mapExpansion,
                                                                 String methodNameForErrorReporting,
                                                                 ParameterizedType startingPointForErrorReporting,
                                                                 Position positionForErrorReporting) {
        Map<Integer, Expression> evaluatedExpressions = new HashMap<>();
        boolean changes = true;
        while (changes) {
            changes = false;
            Expression evaluatedExpression = null;
            // we know that all method candidates have an identical amount of parameters
            Integer pos = findParameterWithoutFunctionalInterfaceTypeOnAnyMethodCandidate(expressionContext, methodCandidates, evaluatedExpressions.keySet());
            if (pos == null) {
                pos = findParameterWhereUnevaluatedLambdaWillHelp(expressionContext, expressions, methodCandidates, evaluatedExpressions.keySet());
            }
            if (pos != null) {
                evaluatedExpression = expressionContext.parseExpression(expressions.get(pos));
            } else {
                Pair<MethodTypeParameterMap, Integer> pair = findParameterWithASingleFunctionalInterfaceType(expressionContext, methodCandidates, evaluatedExpressions.keySet());
                if (pair != null) {
                    pos = pair.v;
                    MethodTypeParameterMap abstractInterfaceMethod = determineAbstractInterfaceMethod(expressionContext.typeContext, pair.k, pos, singleAbstractMethod);
                    if (abstractInterfaceMethod != null) {
                        evaluatedExpression = expressionContext.parseExpression(expressions.get(pos), abstractInterfaceMethod);
                    } else {
                        throw new UnsupportedOperationException("Was not able to create abstract interface method??");
                    }
                }
            }
            if (pos != null) {
                evaluatedExpressions.put(pos, Objects.requireNonNull(evaluatedExpression));
                changes = filterMethodCandidates(expressionContext, evaluatedExpression, pos, methodCandidates) && !methodCandidates.isEmpty();
            }
        }
        // now we need to ensure that there is only 1 method left. It is quite possible that the list is not yet empty,
        // but they should all be overloads of each other; the 1st one is the one most down the hierarchy
        if (methodCandidates.isEmpty())
            throw new UnsupportedOperationException("No candidate found for method " + methodNameForErrorReporting + " in type "
                    + startingPointForErrorReporting.detailedString() + " at position " + positionForErrorReporting);
        if (methodCandidates.size() > 1) {
            trimVarargsVsMethodsWithFewerParameters(methodCandidates);
            if (methodCandidates.size() > 1) {
                TypeContext.MethodCandidate mc0 = methodCandidates.get(0);
                Set<MethodInfo> overloads = mc0.method.methodInfo.typeInfo.overloads(mc0.method.methodInfo, expressionContext.typeContext);
                for (TypeContext.MethodCandidate mcN : methodCandidates.subList(1, methodCandidates.size())) {
                    if (!overloads.contains(mcN.method.methodInfo) && mcN.method.methodInfo != mc0.method.methodInfo) {
                        throw new UnsupportedOperationException("Not all candidates are overloads of the 1st one! No unique " + methodNameForErrorReporting + " not found in known type "
                                + startingPointForErrorReporting.detailedString() + " at position " + positionForErrorReporting);
                    }
                }
            }
        }
        MethodTypeParameterMap method = methodCandidates.get(0).method;
        // now parse the lambda's with our new info

        log(METHOD_CALL, "Reevaluating parameter expressions, single abstract method {}", singleAbstractMethod == null ? "-" : singleAbstractMethod.methodInfo.distinguishingName());
        for (int i = 0; i < expressions.size(); i++) {
            Expression e = evaluatedExpressions.get(i);
            if (e == null || e instanceof UnevaluatedLambdaExpression) {
                MethodTypeParameterMap abstractInterfaceMethod = determineAbstractInterfaceMethod(expressionContext.typeContext, method, i, singleAbstractMethod);
                // note that abstractInterfaceMethod can be null! there's no guarantee we're dealing with a functional interface here
                Expression reParsed = expressionContext.parseExpression(expressions.get(i), abstractInterfaceMethod);
                newParameterExpressions.add(reParsed);
            } else {
                newParameterExpressions.add(e);
            }
        }

        // fill in the map expansion
        int i = 0;
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

    // List.of() vs List.of(E[]...) -> the one with fewer parameters gets priority
    private static void trimVarargsVsMethodsWithFewerParameters(List<TypeContext.MethodCandidate> methodCandidates) {
        int min = methodCandidates.stream().mapToInt(mc -> mc.method.methodInfo.methodInspection.get().parameters.size()).min().orElseThrow();
        methodCandidates.removeIf(mc -> mc.method.methodInfo.methodInspection.get().parameters.size() > min);
    }

    private static Integer findParameterWhereUnevaluatedLambdaWillHelp(ExpressionContext expressionContext, List<com.github.javaparser.ast.expr.Expression> expressions, List<TypeContext.MethodCandidate> methodCandidates, Set<Integer> keySet) {
        return null;
        // TODO
    }

    private static Pair<MethodTypeParameterMap, Integer> findParameterWithASingleFunctionalInterfaceType(ExpressionContext expressionContext,
                                                                                                         List<TypeContext.MethodCandidate> methodCandidates,
                                                                                                         Set<Integer> ignore) {
        if (methodCandidates.isEmpty()) return null;
        MethodInspection mi0 = methodCandidates.get(0).method.methodInfo.methodInspection.get();
        for (int i = 0; i < mi0.parameters.size(); i++) {
            if (!ignore.contains(i)) {
                ParameterizedType functionalInterface = null;
                TypeContext.MethodCandidate mcOfFunctionalInterface = null;
                MethodTypeParameterMap singleAbstractMethod = null;
                for (TypeContext.MethodCandidate mc : methodCandidates) {
                    MethodInspection mi = mc.method.methodInfo.methodInspection.get();
                    ParameterInfo pi = mi.parameters.get(i);
                    boolean isFunctionalInterface = pi.parameterizedType.isFunctionalInterface(expressionContext.typeContext);
                    if (isFunctionalInterface) {
                        if (functionalInterface == null) {
                            functionalInterface = pi.parameterizedType;
                            mcOfFunctionalInterface = mc;
                            singleAbstractMethod = pi.parameterizedType.findSingleAbstractMethodOfInterface(expressionContext.typeContext);
                        } else {
                            MethodTypeParameterMap sam2 = pi.parameterizedType.findSingleAbstractMethodOfInterface(expressionContext.typeContext);
                            if (!compatibleFunctionalInterfaces(sam2, singleAbstractMethod)) {
                                log(METHOD_CALL, "Incompatible functional interfaces {} and {} on method overloads {} and {}",
                                        pi.parameterizedType.detailedString(), functionalInterface.detailedString(),
                                        mi.distinguishingName, mcOfFunctionalInterface.method.methodInfo.distinguishingName());
                                functionalInterface = null;
                                break;
                            }
                        }
                    }
                }
                if (functionalInterface != null) return new Pair<>(mcOfFunctionalInterface.method, i);
            }
        }
        return null;
    }

    private static boolean compatibleFunctionalInterfaces(MethodTypeParameterMap sam1, MethodTypeParameterMap sam2) {
        MethodInspection mi1 = sam1.methodInfo.methodInspection.get();
        MethodInspection mi2 = sam2.methodInfo.methodInspection.get();
        if (mi1.parameters.size() != mi2.parameters.size()) return false;
        //TODO we can do better, can we?
        return mi1.returnType.isVoid() == mi2.returnType.isVoid();
    }


    private static Integer findParameterWithoutFunctionalInterfaceTypeOnAnyMethodCandidate(ExpressionContext expressionContext,
                                                                                           List<TypeContext.MethodCandidate> methodCandidates,
                                                                                           Set<Integer> ignore) {
        if (methodCandidates.isEmpty()) return null;
        MethodInspection mi0 = methodCandidates.get(0).method.methodInfo.methodInspection.get();
        for (int i = 0; i < mi0.parameters.size(); i++) {
            if (!ignore.contains(i)) {
                boolean ok = true;
                for (TypeContext.MethodCandidate mc : methodCandidates) {
                    MethodInspection mi = mc.method.methodInfo.methodInspection.get();
                    ParameterInfo pi = mi.parameters.get(i);
                    boolean isFunctionalInterface = pi.parameterizedType.isFunctionalInterface(expressionContext.typeContext);
                    if (isFunctionalInterface) {
                        ok = false;
                        break;
                    }
                }
                if (ok) return i;
            }
        }
        return null;
    }

    private static boolean filterMethodCandidates(ExpressionContext expressionContext, Expression evaluatedExpression, Integer pos, List<TypeContext.MethodCandidate> methodCandidates) {
        return methodCandidates.removeIf(mc -> !compatibleParameter(expressionContext, evaluatedExpression, pos, mc.method.methodInfo.methodInspection.get()));
    }

    private static boolean compatibleParameter(ExpressionContext expressionContext, Expression evaluatedExpression, Integer pos, MethodInspection methodInspection) {
        List<ParameterInfo> params = methodInspection.parameters;

        ParameterInfo parameterInDefinition;
        if (pos >= params.size()) {
            ParameterInfo lastParameter = params.get(params.size() - 1);
            if (lastParameter.parameterInspection.get().varArgs) {
                parameterInDefinition = lastParameter;
            } else {
                return false;
            }
        } else {
            parameterInDefinition = methodInspection.parameters.get(pos);
        }
        if (evaluatedExpression == EmptyExpression.EMPTY_EXPRESSION) {
            return false;
        }
        if (evaluatedExpression instanceof UnevaluatedLambdaExpression) {
            MethodTypeParameterMap sam = parameterInDefinition.parameterizedType.findSingleAbstractMethodOfInterface(expressionContext.typeContext);
            if (sam == null) return false;
            int numberOfParameters = ((UnevaluatedLambdaExpression) evaluatedExpression).numberOfParameters;
            // if numberOfParameters < 0, we don't even know for sure how many params we're going to get
            // TODO this can be done better? but it should cover 99% of cases
            return numberOfParameters < 0 || sam.methodInfo.methodInspection.get().parameters.size() == numberOfParameters;
        }
        ParameterizedType returnType = evaluatedExpression.returnType();
        return parameterInDefinition.parameterizedType.isAssignableFrom(returnType);
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
            log(METHOD_CALL, "Evaluating parameter expression #{}: {}", parameterExpressions.size(), expr);
            // some expressions (Lambdas, Method references) need to be evaluated in the context of a function interface (singleAbstractMethod)
            // but we don't have that yet, we need the correct method candidate for that
            // therefore, in this first pass, lambdas and method references may return an "UnevaluatedLambdaExpression", which contains minimal
            // information (number of parameters, return type, ...) to help with method overload

            // note that we're not a validating compiler; we assume that the source code that we operate on, is valid :-)
            // that surely grants us some shortcuts
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
