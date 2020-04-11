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
import org.e2immu.analyser.model.expression.UnevaluatedMethodCall;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.e2immu.analyser.model.ParameterizedType.NOT_ASSIGNABLE;
import static org.e2immu.analyser.util.Logger.LogTarget.METHOD_CALL;
import static org.e2immu.analyser.util.Logger.LogTarget.RESOLVE;
import static org.e2immu.analyser.util.Logger.log;

public class ParseMethodCallExpr {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseMethodCallExpr.class);

    public static Expression parse(ExpressionContext expressionContext, MethodCallExpr methodCallExpr, MethodTypeParameterMap singleAbstractMethod) {
        log(METHOD_CALL, "Start parsing method call {}, method name {}, single abstract {}", methodCallExpr,
                methodCallExpr.getNameAsString(), singleAbstractMethod);

        Expression scope = methodCallExpr.getScope().map(expressionContext::parseExpression).orElse(null);
        // depending on the object, we'll need to find the method somewhere
        ParameterizedType scopeType;
        if (scope == null) {
            scopeType = new ParameterizedType(expressionContext.enclosingType, 0);
        } else {
            scopeType = scope.returnType();
        }
        Map<NamedType, ParameterizedType> scopeTypeMap = scopeType.initialTypeParameterMap();
        log(METHOD_CALL, "Type map of method call {} is {}", methodCallExpr.getNameAsString(), scopeTypeMap);
        String methodName = methodCallExpr.getName().asString();
        List<TypeContext.MethodCandidate> methodCandidates = new ArrayList<>();
        expressionContext.typeContext.recursivelyResolveOverloadedMethods(scopeType, methodName, methodCallExpr.getArguments().size(), false, scopeTypeMap, methodCandidates);
        if (methodCandidates.isEmpty()) {
            log(METHOD_CALL, "Creating unevaluated method call, no candidates found");
            return new UnevaluatedMethodCall(methodName);
        }
        List<Expression> newParameterExpressions = new ArrayList<>();
        Map<NamedType, ParameterizedType> mapExpansion = new HashMap<>();

        MethodTypeParameterMap combinedSingleAbstractMethod = singleAbstractMethod == null ? new MethodTypeParameterMap(null, scopeTypeMap) :
                singleAbstractMethod.expand(scopeTypeMap);

        MethodTypeParameterMap method = chooseCandidateAndEvaluateCall(expressionContext, methodCandidates,
                methodCallExpr.getArguments(), newParameterExpressions, combinedSingleAbstractMethod,
                mapExpansion,
                "method " + methodName,
                scopeType,
                methodCallExpr.getBegin().orElseThrow());
        if (method == null) {
            log(METHOD_CALL, "Creating unevaluated method call for {} after failing to choose a candidate", methodName);
            return new UnevaluatedMethodCall(methodName);
        }
        log(METHOD_CALL, "End parsing method call {}, return types of method parameters [{}], concrete type {}, mapExpansion {}",
                methodCallExpr, StringUtil.join(newParameterExpressions, Expression::returnType),
                method.getConcreteReturnType().detailedString(), mapExpansion);

        if (method.methodInfo.typeInfo == expressionContext.enclosingType) {
            // only when we're in the same type shall we add method dependencies
            log(RESOLVE, "Add method dependency to {}", method.methodInfo.name);
            expressionContext.dependenciesOnOtherMethodsAndFields.add(method.methodInfo);
        }
        return new MethodCall(scope, mapExpansion.isEmpty() ? method : method.expand(mapExpansion), newParameterExpressions);
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
        Map<MethodInfo, Integer> compatibilityScore = new HashMap<>();
        while (true) {
            Expression evaluatedExpression = null;
            // we know that all method candidates have an identical amount of parameters
            Integer pos = findParameterWithoutFunctionalInterfaceTypeOnAnyMethodCandidate(expressionContext, methodCandidates, evaluatedExpressions.keySet());
            if (pos == null) {
                pos = findParameterWhereUnevaluatedLambdaWillHelp(expressionContext, expressions, methodCandidates, evaluatedExpressions.keySet());
            }
            if (pos != null) {
                evaluatedExpression = expressionContext.parseExpression(expressions.get(pos), singleAbstractMethod == null ? null : singleAbstractMethod.copyWithoutMethod());
            } else {
                Pair<MethodTypeParameterMap, Integer> pair = findParameterWithASingleFunctionalInterfaceType(expressionContext, methodCandidates, evaluatedExpressions.keySet());
                if (pair != null) {
                    pos = pair.v;
                    MethodTypeParameterMap abstractInterfaceMethod = determineAbstractInterfaceMethod(expressionContext.typeContext, pair.k, pos, singleAbstractMethod);
                    evaluatedExpression = expressionContext.parseExpression(expressions.get(pos), abstractInterfaceMethod);
                }
            }
            if (pos != null) {
                evaluatedExpressions.put(pos, Objects.requireNonNull(evaluatedExpression));
                filterMethodCandidates(expressionContext, evaluatedExpression, pos, methodCandidates, compatibilityScore);
                if (methodCandidates.isEmpty() || evaluatedExpressions.size() == expressions.size()) break;
            } else {
                break;
            }
        }
        // now we need to ensure that there is only 1 method left, but, there can be overloads and
        // methods with implicit type conversions, varargs, etc. etc.
        if (methodCandidates.isEmpty()) {
            log(METHOD_CALL, "Evaluated expressions for {}: ", methodNameForErrorReporting);
            evaluatedExpressions.forEach((i, expr) -> LOGGER.warn("  {} = {}", i, expr.expressionString(0)));
            log(METHOD_CALL, "No candidate found for {} in type {} at position {}", methodNameForErrorReporting,
                    startingPointForErrorReporting.detailedString(), positionForErrorReporting);
            return null;
        }
        if (methodCandidates.size() > 1) {
            trimMethodsWithBestScore(methodCandidates, compatibilityScore);
            if (methodCandidates.size() > 1) {
                trimVarargsVsMethodsWithFewerParameters(methodCandidates);
                if (methodCandidates.size() > 1) {
                    //methodCandidates.sort(expressionContext.typeContext::compareMethodCandidates);
                    TypeContext.MethodCandidate mc0 = methodCandidates.get(0);
                    Set<MethodInfo> overloads = mc0.method.methodInfo.typeInfo.overloads(mc0.method.methodInfo, expressionContext.typeContext);
                    for (TypeContext.MethodCandidate mcN : methodCandidates.subList(1, methodCandidates.size())) {
                        if (!overloads.contains(mcN.method.methodInfo) && mcN.method.methodInfo != mc0.method.methodInfo) {
                            for (TypeContext.MethodCandidate mc : methodCandidates) {
                                log(METHOD_CALL, "Candidate: {} score {}", mc.method.methodInfo.distinguishingName(), compatibilityScore.get(mc.method.methodInfo));
                            }
                            for (MethodInfo overload : overloads) {
                                log(METHOD_CALL, "Overloads of 1st: {}", overload.distinguishingName());
                            }
                            log(METHOD_CALL, "Not all candidates of {} are overloads of the 1st one! {} at position {}", methodNameForErrorReporting,
                                    startingPointForErrorReporting.detailedString(), positionForErrorReporting);
                            return null;
                        }
                    }
                }
            }
        }
        MethodTypeParameterMap method = methodCandidates.get(0).method;
        // now parse the lambda's with our new info
        log(METHOD_CALL, "Found method {}", method.methodInfo.distinguishingName());

        for (int i = 0; i < expressions.size(); i++) {
            Expression e = evaluatedExpressions.get(i);
            if (e == null || e instanceof UnevaluatedLambdaExpression || e instanceof UnevaluatedMethodCall) {
                log(METHOD_CALL, "Reevaluating unevaluated expression on {}, pos {}, single abstract method {}",
                        methodNameForErrorReporting, i, singleAbstractMethod);
                MethodTypeParameterMap abstractInterfaceMethod = determineAbstractInterfaceMethod(expressionContext.typeContext, method, i, singleAbstractMethod);
                // note that abstractInterfaceMethod can be null! there's no guarantee we're dealing with a functional interface here
                if(e instanceof UnevaluatedMethodCall && abstractInterfaceMethod == null && singleAbstractMethod != null) {
                    ParameterInfo parameterInfo = method.methodInfo.methodInspection.get().parameters.get(i);
                    ParameterizedType parameterizedType = parameterInfo.parameterizedType; // Collector<T,A,R>, with T linked to T0 of Stream
                    ParameterizedType formalParameterizedType = parameterizedType.typeInfo.asParameterizedType();
                    Map<NamedType, ParameterizedType> links = new HashMap<>();
                    int pos = 0;
                    for (ParameterizedType typeParameter : formalParameterizedType.parameters) {
                        links.put(typeParameter.typeParameter, parameterizedType.parameters.get(pos));
                        pos++;
                    }
                    abstractInterfaceMethod = singleAbstractMethod.expand(links);
                }
                Expression reParsed = expressionContext.parseExpression(expressions.get(i), abstractInterfaceMethod == null ? singleAbstractMethod : abstractInterfaceMethod);
                if (reParsed instanceof UnevaluatedMethodCall || reParsed instanceof UnevaluatedLambdaExpression) {
                    log(METHOD_CALL, "Reevaluation of {} fails, have {}", methodNameForErrorReporting, reParsed.expressionString(0));
                    return null;
                }
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
            ParameterizedType concreteTypeInMethod = method.getConcreteTypeOfParameter(i);

            translated.forEach((k, v) -> {
                // we can go in two directions here.
                // either the type parameter gets a proper value by the concreteParameterType, or the concreteParameter type should
                // agree with the concrete types map in the method candidate. It is quite possible that concreteParameterType == ParameterizedType.NULL,
                // and then the value in the map should prevail
                ParameterizedType valueToAdd;
                if (concreteTypeInMethod.betterDefinedThan(v)) {
                    valueToAdd = concreteTypeInMethod;
                } else {
                    valueToAdd = v;
                }
                if (!mapExpansion.containsKey(k)) mapExpansion.put(k, valueToAdd);
            });
            i++;
            if (i >= formalParameters.size()) break; // varargs... we have more than there are
        }
        return method;
    }

    private static void trimMethodsWithBestScore(List<TypeContext.MethodCandidate> methodCandidates, Map<MethodInfo, Integer> compatibilityScore) {
        int min = methodCandidates.stream().mapToInt(mc -> compatibilityScore.getOrDefault(mc.method.methodInfo, 0)).min().orElseThrow();
        if (min == NOT_ASSIGNABLE) throw new UnsupportedOperationException();
        methodCandidates.removeIf(mc -> compatibilityScore.getOrDefault(mc.method.methodInfo, 0) > min);
    }

    // remove varargs if there's also non-varargs solutions
    //
    // this step if AFTER the score step, so we've already dealt with type conversions.
    // we still have to deal with overloads in supertypes, methods with the same type signature
    private static void trimVarargsVsMethodsWithFewerParameters(List<TypeContext.MethodCandidate> methodCandidates) {
        int countVarargs = (int) methodCandidates.stream().filter(mc -> mc.method.methodInfo.isVarargs()).count();
        if (countVarargs > 0 && countVarargs < methodCandidates.size()) {
            methodCandidates.removeIf(mc -> mc.method.methodInfo.isVarargs());
        }
    }

    // File.listFiles(FileNameFilter) vs File.listFiles(FileFilter): both types take a functional interface with a different number of parameters
    // (fileFilter takes 1, fileNameFilter takes 2)
    private static Integer findParameterWhereUnevaluatedLambdaWillHelp(ExpressionContext expressionContext, List<com.github.javaparser.ast.expr.Expression> expressions, List<TypeContext.MethodCandidate> methodCandidates, Set<Integer> ignore) {
        if (methodCandidates.isEmpty()) return null;
        MethodInspection mi0 = methodCandidates.get(0).method.methodInfo.methodInspection.get();
        for (int i = 0; i < mi0.parameters.size(); i++) {
            com.github.javaparser.ast.expr.Expression expression = expressions.get(i);
            if (!ignore.contains(i) && (expression.isLambdaExpr() || expression.isMethodReferenceExpr())) {
                Set<Integer> numberOfParametersInFunctionalInterface = new HashSet<>();
                boolean success = true;
                for (TypeContext.MethodCandidate mc : methodCandidates) {
                    MethodInspection mi = mc.method.methodInfo.methodInspection.get();
                    ParameterInfo pi = mi.parameters.get(i);
                    boolean isFunctionalInterface = pi.parameterizedType.isFunctionalInterface(expressionContext.typeContext);
                    if (isFunctionalInterface) {
                        MethodTypeParameterMap singleAbstractMethod = pi.parameterizedType.findSingleAbstractMethodOfInterface(expressionContext.typeContext);
                        int numberOfParameters = singleAbstractMethod.methodInfo.methodInspection.get().parameters.size();
                        boolean added = numberOfParametersInFunctionalInterface.add(numberOfParameters);
                        if (!added) {
                            success = false;
                            break;
                        }
                    }
                }
                if (success) return i;
            }
        }
        return null;
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
                    if (i < mi.parameters.size()) {
                        ParameterInfo pi = mi.parameters.get(i);
                        boolean isFunctionalInterface = pi.parameterizedType.isFunctionalInterface(expressionContext.typeContext);
                        if (isFunctionalInterface) {
                            if (functionalInterface == null) {
                                functionalInterface = pi.parameterizedType;
                                mcOfFunctionalInterface = mc;
                                singleAbstractMethod = pi.parameterizedType.findSingleAbstractMethodOfInterface(expressionContext.typeContext);
                            } else {
                                MethodTypeParameterMap sam2 = pi.parameterizedType.findSingleAbstractMethodOfInterface(expressionContext.typeContext);
                                if (!singleAbstractMethod.isAssignableFrom(sam2)) {
                                    log(METHOD_CALL, "Incompatible functional interfaces {} and {} on method overloads {} and {}",
                                            pi.parameterizedType.detailedString(), functionalInterface.detailedString(),
                                            mi.distinguishingName, mcOfFunctionalInterface.method.methodInfo.distinguishingName());
                                    functionalInterface = null;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (functionalInterface != null) return new Pair<>(mcOfFunctionalInterface.method, i);
            }
        }
        return null;
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
                    if (i < mi.parameters.size()) {
                        ParameterInfo pi = mi.parameters.get(i);
                        boolean isFunctionalInterface = pi.parameterizedType.isFunctionalInterface(expressionContext.typeContext);
                        if (isFunctionalInterface) {
                            ok = false;
                            break;
                        }
                    } else {
                        // we have more parameters than mi, which is using a varargs
                        ok = false;
                    }
                }
                if (ok) return i;
            }
        }
        return null;
    }

    private static void filterMethodCandidates(ExpressionContext expressionContext,
                                               Expression evaluatedExpression,
                                               Integer pos,
                                               List<TypeContext.MethodCandidate> methodCandidates,
                                               Map<MethodInfo, Integer> compatibilityScore) {
        methodCandidates.removeIf(mc -> {
            int score = compatibleParameter(expressionContext, evaluatedExpression, pos, mc.method.methodInfo.methodInspection.get());
            if (score >= 0) {
                Integer inMap = compatibilityScore.get(mc.method.methodInfo);
                inMap = inMap == null ? score : score + inMap;
                compatibilityScore.put(mc.method.methodInfo, inMap);
            }
            return score < 0;
        });
    }

    // different situations with varargs: method(int p1, String... args)
    // 1: method(1) is possible, but pos will not get here, so there's no reason for incompatibility
    // 2: pos == params.size()-1: method(p, "abc")
    // 3: pos == params.size()-1: method(p, new String[] { "a", "b"} )
    // 4: pos >= params.size(): method(p, "a", "b")  -> we need the base type
    private static int compatibleParameter(ExpressionContext expressionContext, Expression evaluatedExpression, Integer pos, MethodInspection methodInspection) {
        if (evaluatedExpression == EmptyExpression.EMPTY_EXPRESSION) {
            return NOT_ASSIGNABLE;
        }
        List<ParameterInfo> params = methodInspection.parameters;

        if (pos >= params.size()) {
            ParameterInfo lastParameter = params.get(params.size() - 1);
            if (lastParameter.parameterInspection.get().varArgs) {
                ParameterizedType typeOfParameter = lastParameter.parameterizedType.copyWithOneFewerArrays();
                return compatibleParameter(expressionContext, evaluatedExpression, typeOfParameter);
            }
            return NOT_ASSIGNABLE;
        }
        ParameterInfo parameterInfo = params.get(pos);
        if (pos == params.size() - 1 && parameterInfo.parameterInspection.get().varArgs) {
            int withArrays = compatibleParameter(expressionContext, evaluatedExpression, parameterInfo.parameterizedType);
            int withoutArrays = compatibleParameter(expressionContext, evaluatedExpression, parameterInfo.parameterizedType.copyWithOneFewerArrays());
            if (withArrays == -1) return withoutArrays;
            if (withoutArrays == -1) return withArrays;
            return Math.min(withArrays, withoutArrays);
        }
        // the normal situation
        return compatibleParameter(expressionContext, evaluatedExpression, parameterInfo.parameterizedType);
    }

    private static int compatibleParameter(ExpressionContext expressionContext, Expression evaluatedExpression, ParameterizedType typeOfParameter) {
        if (evaluatedExpression instanceof UnevaluatedMethodCall) {
            return 1; // we'll just have to redo this
        }
        if (evaluatedExpression instanceof UnevaluatedLambdaExpression) {
            MethodTypeParameterMap sam = typeOfParameter.findSingleAbstractMethodOfInterface(expressionContext.typeContext);
            if (sam == null) return NOT_ASSIGNABLE;
            int numberOfParametersInSam = sam.methodInfo.methodInspection.get().parameters.size();
            return ((UnevaluatedLambdaExpression) evaluatedExpression).numberOfParameters.contains(numberOfParametersInSam) ? 0 : NOT_ASSIGNABLE;
        }
        ParameterizedType returnType = evaluatedExpression.returnType();
        if (typeOfParameter.isFunctionalInterface(expressionContext.typeContext) && returnType.isFunctionalInterface(expressionContext.typeContext)) {
            MethodTypeParameterMap sam1 = typeOfParameter.findSingleAbstractMethodOfInterface(expressionContext.typeContext);
            MethodTypeParameterMap sam2 = returnType.findSingleAbstractMethodOfInterface(expressionContext.typeContext);
            return sam1.isAssignableFrom(sam2) ? 0 : NOT_ASSIGNABLE;
        }
        return typeOfParameter.numericIsAssignableFrom(returnType);
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
        Objects.requireNonNull(method);
        MethodTypeParameterMap abstractInterfaceMethod = method.getConcreteTypeOfParameter(p).findSingleAbstractMethodOfInterface(typeContext);
        log(METHOD_CALL, "Abstract interface method of parameter {} of method {} is {}", p, method.methodInfo.fullyQualifiedName(), abstractInterfaceMethod);
        if (abstractInterfaceMethod == null || singleAbstractMethod == null) return abstractInterfaceMethod;

        if (singleAbstractMethod.isSingleAbstractMethod() && singleAbstractMethod.methodInfo.typeInfo.equals(method.methodInfo.typeInfo)) {
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
