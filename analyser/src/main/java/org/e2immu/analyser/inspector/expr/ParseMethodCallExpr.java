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

package org.e2immu.analyser.inspector.expr;

import com.github.javaparser.Position;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.inspector.MethodTypeParameterMap;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.resolver.ShallowMethodResolver;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.e2immu.analyser.model.ParameterizedType.NOT_ASSIGNABLE;
import static org.e2immu.analyser.util.Logger.LogTarget.METHOD_CALL;
import static org.e2immu.analyser.util.Logger.log;

public record ParseMethodCallExpr(InspectionProvider inspectionProvider) {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseMethodCallExpr.class);

    public Expression parse(ExpressionContext expressionContext, MethodCallExpr methodCallExpr, MethodTypeParameterMap singleAbstractMethod) {
        log(METHOD_CALL, "Start parsing method call {}, method name {}, single abstract {}", methodCallExpr,
                methodCallExpr.getNameAsString(), singleAbstractMethod);

        Expression scope = methodCallExpr.getScope().map(expressionContext::parseExpression).orElse(null);
        // depending on the object, we'll need to find the method somewhere
        ParameterizedType scopeType;

        if (scope == null) {
            scopeType = new ParameterizedType(expressionContext.enclosingType, 0);
        } else {
            scopeType = scope.returnType();
            if (scope instanceof VariableExpression variableExpression) {
                if (variableExpression.variable() instanceof This v) {
                    if (v.writeSuper) {
                        scopeType = inspectionProvider.getTypeInspection(v.typeInfo).parentClass();
                    }
                }
            }
        }
        Map<NamedType, ParameterizedType> scopeTypeMap = scopeType.initialTypeParameterMap(inspectionProvider);
        log(METHOD_CALL, "Type map of method call {} is {}", methodCallExpr.getNameAsString(), scopeTypeMap);
        String methodName = methodCallExpr.getName().asString();
        List<TypeContext.MethodCandidate> methodCandidates = new ArrayList<>();
        expressionContext.typeContext.recursivelyResolveOverloadedMethods(scopeType, methodName,
                methodCallExpr.getArguments().size(), false, scopeTypeMap, methodCandidates);
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

        MethodInfo methodInfo = method.methodInspection.getMethodInfo();
        Expression computedScope;
        boolean objectIsImplicit = scope == null;
        if (objectIsImplicit) {
            if (method.methodInspection.isStatic()) {
                computedScope = new TypeExpression(methodInfo.typeInfo.asParameterizedType(inspectionProvider));
            } else {
                Variable thisVariable = new This(inspectionProvider, expressionContext.enclosingType);
                computedScope = new VariableExpression(thisVariable);
            }
        } else {
            computedScope = scope;
        }
        // TODO check that getConcreteReturnType() is correct here (20201204)
        return new MethodCall(objectIsImplicit, computedScope, methodInfo, mapExpansion.isEmpty() ? method.getConcreteReturnType() :
                method.expand(mapExpansion).getConcreteReturnType(), newParameterExpressions,
                ObjectFlow.NYE);
    }

    MethodTypeParameterMap chooseCandidateAndEvaluateCall(ExpressionContext expressionContext,
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
        if (!expressions.isEmpty()) {
            while (true) {
                Expression evaluatedExpression = null;
                // we know that all method candidates have an identical amount of parameters
                Integer pos = findParameterWithoutFunctionalInterfaceTypeOnAnyMethodCandidate(methodCandidates, evaluatedExpressions.keySet());
                if (pos == null) {
                    pos = findParameterWhereUnevaluatedLambdaWillHelp(expressions, methodCandidates, evaluatedExpressions.keySet());
                }
                if (pos != null) {
                    evaluatedExpression = expressionContext.parseExpression(expressions.get(pos), singleAbstractMethod == null ? null : singleAbstractMethod.copyWithoutMethod());
                } else {
                    Pair<MethodTypeParameterMap, Integer> pair = findParameterWithASingleFunctionalInterfaceType(methodCandidates,
                            evaluatedExpressions.keySet());
                    if (pair != null) {
                        pos = pair.v;
                        MethodTypeParameterMap abstractInterfaceMethod = determineAbstractInterfaceMethod(pair.k, pos, singleAbstractMethod);
                        evaluatedExpression = expressionContext.parseExpression(expressions.get(pos), abstractInterfaceMethod);
                    }
                }
                if (pos != null) {
                    evaluatedExpressions.put(pos, Objects.requireNonNull(evaluatedExpression));
                    filterMethodCandidates(evaluatedExpression, pos, methodCandidates, compatibilityScore);
                    if (methodCandidates.isEmpty() || evaluatedExpressions.size() == expressions.size()) break;
                } else {
                    break;
                }
            }
        }
        // now we need to ensure that there is only 1 method left, but, there can be overloads and
        // methods with implicit type conversions, varargs, etc. etc.
        if (methodCandidates.isEmpty()) {
            log(METHOD_CALL, "Evaluated expressions for {}: ", methodNameForErrorReporting);
            evaluatedExpressions.forEach((i, expr) -> LOGGER.warn("  {} = {}", i, expr.debugOutput()));
            log(METHOD_CALL, "No candidate found for {} in type {} at position {}", methodNameForErrorReporting,
                    startingPointForErrorReporting.detailedString(), positionForErrorReporting);
            return null;
        }
        if (methodCandidates.size() > 1) {
            trimMethodsWithBestScore(methodCandidates, compatibilityScore);
            if (methodCandidates.size() > 1) {
                trimVarargsVsMethodsWithFewerParameters(methodCandidates);
                if (methodCandidates.size() > 1) {
                    //methodCandidates.sort(inspectionProvider::compareMethodCandidates);
                    TypeContext.MethodCandidate mc0 = methodCandidates.get(0);
                    // we cannot rely on methodResolution yet...
                    Set<MethodInfo> overrides = ShallowMethodResolver.overrides(inspectionProvider, mc0.method().methodInspection.getMethodInfo());
                    for (TypeContext.MethodCandidate mcN : methodCandidates.subList(1, methodCandidates.size())) {
                        if (!overrides.contains(mcN.method().methodInspection.getMethodInfo()) &&
                                mcN.method().methodInspection.getMethodInfo() != mc0.method().methodInspection.getMethodInfo()) {
                            for (TypeContext.MethodCandidate mc : methodCandidates) {
                                log(METHOD_CALL, "Candidate: {} score {}", mc.method().methodInspection.getFullyQualifiedName(),
                                        compatibilityScore.get(mc.method().methodInspection.getMethodInfo()));
                            }
                            for (MethodInfo override : overrides) {
                                log(METHOD_CALL, "Overrides of 1st: {}", override.distinguishingName());
                            }
                            log(METHOD_CALL, "Not all candidates of {} are overrides of the 1st one! {} at position {}", methodNameForErrorReporting,
                                    startingPointForErrorReporting.detailedString(), positionForErrorReporting);
                            return null;
                        }
                    }
                }
            }
        }
        MethodTypeParameterMap method = methodCandidates.get(0).method();
        // now parse the lambda's with our new info
        log(METHOD_CALL, "Found method {}", method.methodInspection.getFullyQualifiedName());

        for (int i = 0; i < expressions.size(); i++) {
            Expression e = evaluatedExpressions.get(i);
            if (e == null || e instanceof UnevaluatedLambdaExpression || e instanceof UnevaluatedMethodCall) {
                log(METHOD_CALL, "Reevaluating unevaluated expression on {}, pos {}, single abstract method {}",
                        methodNameForErrorReporting, i, singleAbstractMethod);
                MethodTypeParameterMap mapForEvaluation;

                MethodTypeParameterMap abstractInterfaceMethod = determineAbstractInterfaceMethod(method, i, singleAbstractMethod);
                if (abstractInterfaceMethod != null) {
                    if (singleAbstractMethod != null) {
                        ParameterizedType returnTypeOfMethod = method.methodInspection.getReturnType();
                        if (returnTypeOfMethod.typeInfo != null) {
                            // a type with its own formal parameters, we try to jump from the concrete value in the AIM (entry.getValue) to
                            // the type parameter in the method's return type (tpInReturnType) to the type parameter in the method's formal return type (tpInFormalReturnType)
                            // to the value in the SAM (best)
                            ParameterizedType formalReturnTypeOfMethod = returnTypeOfMethod.typeInfo.asParameterizedType(inspectionProvider);
                            // we should expect type parameters of the return type to be present in the SAM; the ones of the formal type are in the AIM
                            Map<NamedType, ParameterizedType> newMap = new HashMap<>();
                            for (Map.Entry<NamedType, ParameterizedType> entry : abstractInterfaceMethod.concreteTypes.entrySet()) {
                                ParameterizedType typeParameter = entry.getValue();
                                ParameterizedType best = null;
                                ParameterizedType tpInReturnType = returnTypeOfMethod.parameters.stream().filter(pt -> pt.typeParameter == typeParameter.typeParameter).findFirst().orElse(null);
                                if (tpInReturnType != null) {
                                    assert tpInReturnType.typeParameter != null;
                                    ParameterizedType tpInFormalReturnType = formalReturnTypeOfMethod.parameters.get(tpInReturnType.typeParameter.getIndex());
                                    best = MethodTypeParameterMap.apply(singleAbstractMethod.concreteTypes, tpInFormalReturnType);
                                }
                                if (best == null) {
                                    best = entry.getValue();
                                }
                                newMap.put(entry.getKey(), best);
                            }
                            mapForEvaluation = new MethodTypeParameterMap(abstractInterfaceMethod.methodInspection, newMap);
                        } else {
                            mapForEvaluation = abstractInterfaceMethod;
                        }
                    } else {
                        mapForEvaluation = abstractInterfaceMethod;
                    }
                } else {
                    // could be that e == null, we did not evaluate
                    if (e instanceof UnevaluatedMethodCall && singleAbstractMethod != null) {
                        Map<NamedType, ParameterizedType> newMap = makeTranslationMap(method, i, singleAbstractMethod);
                        mapForEvaluation = new MethodTypeParameterMap(null, newMap);
                    } else {
                        mapForEvaluation = singleAbstractMethod;
                    }
                }
                Expression reParsed = expressionContext.parseExpression(expressions.get(i), mapForEvaluation);
                if (reParsed instanceof UnevaluatedMethodCall || reParsed instanceof UnevaluatedLambdaExpression) {
                    log(METHOD_CALL, "Reevaluation of {} fails, have {}", methodNameForErrorReporting, reParsed.debugOutput());
                    return null;
                }
                newParameterExpressions.add(reParsed);
            } else {
                newParameterExpressions.add(e);
            }
        }

        // fill in the map expansion
        int i = 0;
        List<ParameterInfo> formalParameters = method.methodInspection.getParameters();
        for (Expression expression : newParameterExpressions) {
            log(METHOD_CALL, "Examine parameter {}", i);
            ParameterizedType concreteParameterType = expression.returnType();
            Map<NamedType, ParameterizedType> translated = formalParameters.get(i).parameterizedType
                    .translateMap(inspectionProvider, concreteParameterType);
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

    private Map<NamedType, ParameterizedType> makeTranslationMap(MethodTypeParameterMap method,
                                                                 int i,
                                                                 MethodTypeParameterMap singleAbstractMethod) {
        ParameterInfo parameterInfo = method.methodInspection.getParameters().get(i);
        ParameterizedType parameterizedType = parameterInfo.parameterizedType; // Collector<T,A,R>, with T linked to T0 of Stream
        assert parameterizedType.typeInfo != null;
        ParameterizedType formalParameterizedType = parameterizedType.typeInfo.asParameterizedType(inspectionProvider);
        Map<NamedType, ParameterizedType> newMap = new HashMap<>();
        int pos = 0;
        for (ParameterizedType typeParameter : formalParameterizedType.parameters) {
            ParameterizedType best = MethodTypeParameterMap.apply(singleAbstractMethod.concreteTypes, parameterizedType.parameters.get(pos));
            newMap.put(typeParameter.typeParameter, best);
            pos++;
        }
        return newMap;
    }

    private static void trimMethodsWithBestScore(List<TypeContext.MethodCandidate> methodCandidates, Map<MethodInfo, Integer> compatibilityScore) {
        int min = methodCandidates.stream().mapToInt(mc -> compatibilityScore.getOrDefault(mc.method().methodInspection.getMethodInfo(), 0)).min().orElseThrow();
        if (min == NOT_ASSIGNABLE) throw new UnsupportedOperationException();
        methodCandidates.removeIf(mc -> compatibilityScore.getOrDefault(mc.method().methodInspection.getMethodInfo(), 0) > min);
    }

    // remove varargs if there's also non-varargs solutions
    //
    // this step if AFTER the score step, so we've already dealt with type conversions.
    // we still have to deal with overloads in supertypes, methods with the same type signature
    private static void trimVarargsVsMethodsWithFewerParameters(List<TypeContext.MethodCandidate> methodCandidates) {
        int countVarargs = (int) methodCandidates.stream().filter(mc -> mc.method().methodInspection.isVarargs()).count();
        if (countVarargs > 0 && countVarargs < methodCandidates.size()) {
            methodCandidates.removeIf(mc -> mc.method().methodInspection.isVarargs());
        }
    }

    // File.listFiles(FileNameFilter) vs File.listFiles(FileFilter): both types take a functional interface with a different number of parameters
    // (fileFilter takes 1, fileNameFilter takes 2)
    private Integer findParameterWhereUnevaluatedLambdaWillHelp(
            List<com.github.javaparser.ast.expr.Expression> expressions,
            List<TypeContext.MethodCandidate> methodCandidates,
            Set<Integer> ignore) {
        if (methodCandidates.isEmpty()) return null;
        MethodInspection mi0 = methodCandidates.get(0).method().methodInspection;
        outer:
        for (int i = 0; i < mi0.getParameters().size(); i++) {
            com.github.javaparser.ast.expr.Expression expression = expressions.get(i);
            if (!ignore.contains(i) && (expression.isLambdaExpr() || expression.isMethodReferenceExpr())) {
                Set<Integer> numberOfParametersInFunctionalInterface = new HashSet<>();
                for (TypeContext.MethodCandidate mc : methodCandidates) {
                    MethodInspection mi = mc.method().methodInspection;
                    ParameterInfo pi = mi.getParameters().get(i);
                    boolean isFunctionalInterface = pi.parameterizedType.isFunctionalInterface(inspectionProvider);
                    if (isFunctionalInterface) {
                        MethodTypeParameterMap singleAbstractMethod = pi.parameterizedType.findSingleAbstractMethodOfInterface(inspectionProvider);
                        int numberOfParameters = singleAbstractMethod.methodInspection.getParameters().size();
                        boolean added = numberOfParametersInFunctionalInterface.add(numberOfParameters);
                        if (!added) {
                            continue outer;
                        }
                    }
                }
                return i;
            }
        }
        return null;
    }

    private Pair<MethodTypeParameterMap, Integer> findParameterWithASingleFunctionalInterfaceType(
            List<TypeContext.MethodCandidate> methodCandidates,
            Set<Integer> ignore) {
        if (methodCandidates.isEmpty()) return null;
        MethodInspection mi0 = methodCandidates.get(0).method().methodInspection;
        for (int i = 0; i < mi0.getParameters().size(); i++) {
            if (!ignore.contains(i)) {
                ParameterizedType functionalInterface = null;
                TypeContext.MethodCandidate mcOfFunctionalInterface = null;
                MethodTypeParameterMap singleAbstractMethod = null;
                for (TypeContext.MethodCandidate mc : methodCandidates) {
                    MethodInspection mi = mc.method().methodInspection;
                    if (i < mi.getParameters().size()) {
                        ParameterInfo pi = mi.getParameters().get(i);
                        boolean isFunctionalInterface = pi.parameterizedType.isFunctionalInterface();
                        if (isFunctionalInterface) {
                            if (functionalInterface == null) {
                                functionalInterface = pi.parameterizedType;
                                mcOfFunctionalInterface = mc;
                                singleAbstractMethod = pi.parameterizedType.findSingleAbstractMethodOfInterface(inspectionProvider);
                            } else {
                                MethodTypeParameterMap sam2 = pi.parameterizedType.findSingleAbstractMethodOfInterface(inspectionProvider);
                                if (!singleAbstractMethod.isAssignableFrom(sam2)) {
                                    log(METHOD_CALL, "Incompatible functional interfaces {} and {} on method overloads {} and {}",
                                            pi.parameterizedType.detailedString(), functionalInterface.detailedString(),
                                            mi.getDistinguishingName(), mcOfFunctionalInterface.method().methodInspection.getDistinguishingName());
                                    functionalInterface = null;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (functionalInterface != null) return new Pair<>(mcOfFunctionalInterface.method(), i);
            }
        }
        return null;
    }

    private Integer findParameterWithoutFunctionalInterfaceTypeOnAnyMethodCandidate(
            List<TypeContext.MethodCandidate> methodCandidates, Set<Integer> ignore) {
        if (methodCandidates.isEmpty()) return null;
        MethodInspection mi0 = methodCandidates.get(0).method().methodInspection;
        for (int i = 0; i < mi0.getParameters().size(); i++) {
            if (!ignore.contains(i)) {
                boolean ok = true;
                for (TypeContext.MethodCandidate mc : methodCandidates) {
                    MethodInspection mi = mc.method().methodInspection;
                    if (i < mi.getParameters().size()) {
                        ParameterInfo pi = mi.getParameters().get(i);
                        boolean isFunctionalInterface = pi.parameterizedType.isFunctionalInterface(inspectionProvider);
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

    private void filterMethodCandidates(Expression evaluatedExpression,
                                        Integer pos,
                                        List<TypeContext.MethodCandidate> methodCandidates,
                                        Map<MethodInfo, Integer> compatibilityScore) {
        methodCandidates.removeIf(mc -> {
            int score = compatibleParameter(evaluatedExpression, pos, mc.method().methodInspection);
            if (score >= 0) {
                Integer inMap = compatibilityScore.get(mc.method().methodInspection.getMethodInfo());
                inMap = inMap == null ? score : score + inMap;
                compatibilityScore.put(mc.method().methodInspection.getMethodInfo(), inMap);
            }
            return score < 0;
        });
    }

    // different situations with varargs: method(int p1, String... args)
    // 1: method(1) is possible, but pos will not get here, so there's no reason for incompatibility
    // 2: pos == params.size()-1: method(p, "abc")
    // 3: pos == params.size()-1: method(p, new String[] { "a", "b"} )
    // 4: pos >= params.size(): method(p, "a", "b")  -> we need the base type
    private int compatibleParameter(Expression evaluatedExpression, Integer pos, MethodInspection methodInspection) {
        if (evaluatedExpression == EmptyExpression.EMPTY_EXPRESSION) {
            return NOT_ASSIGNABLE;
        }
        List<ParameterInfo> params = methodInspection.getParameters();

        if (pos >= params.size()) {
            ParameterInfo lastParameter = params.get(params.size() - 1);
            if (lastParameter.parameterInspection.get().isVarArgs()) {
                ParameterizedType typeOfParameter = lastParameter.parameterizedType.copyWithOneFewerArrays();
                return compatibleParameter(evaluatedExpression, typeOfParameter);
            }
            return NOT_ASSIGNABLE;
        }
        ParameterInfo parameterInfo = params.get(pos);
        if (pos == params.size() - 1 && parameterInfo.parameterInspection.get().isVarArgs()) {
            int withArrays = compatibleParameter(evaluatedExpression, parameterInfo.parameterizedType);
            int withoutArrays = compatibleParameter(evaluatedExpression, parameterInfo.parameterizedType.copyWithOneFewerArrays());
            if (withArrays == -1) return withoutArrays;
            if (withoutArrays == -1) return withArrays;
            return Math.min(withArrays, withoutArrays);
        }
        // the normal situation
        return compatibleParameter(evaluatedExpression, parameterInfo.parameterizedType);
    }

    private int compatibleParameter(Expression evaluatedExpression, ParameterizedType typeOfParameter) {
        if (evaluatedExpression instanceof UnevaluatedMethodCall) {
            return 1; // we'll just have to redo this
        }
        if (evaluatedExpression instanceof UnevaluatedLambdaExpression) {
            MethodTypeParameterMap sam = typeOfParameter.findSingleAbstractMethodOfInterface(inspectionProvider);
            if (sam == null) return NOT_ASSIGNABLE;
            int numberOfParametersInSam = sam.methodInspection.getParameters().size();
            return ((UnevaluatedLambdaExpression) evaluatedExpression).numberOfParameters.contains(numberOfParametersInSam) ? 0 : NOT_ASSIGNABLE;
        }
        ParameterizedType returnType = evaluatedExpression.returnType();
        if (typeOfParameter.isFunctionalInterface(inspectionProvider) && returnType.isFunctionalInterface(inspectionProvider)) {
            MethodTypeParameterMap sam1 = typeOfParameter.findSingleAbstractMethodOfInterface(inspectionProvider);
            MethodTypeParameterMap sam2 = returnType.findSingleAbstractMethodOfInterface(inspectionProvider);
            return sam1.isAssignableFrom(sam2) ? 0 : NOT_ASSIGNABLE;
        }
        return typeOfParameter.numericIsAssignableFrom(inspectionProvider, returnType);
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
    private MethodTypeParameterMap determineAbstractInterfaceMethod(MethodTypeParameterMap method,
                                                                    int p,
                                                                    MethodTypeParameterMap singleAbstractMethod) {
        Objects.requireNonNull(method);
        MethodTypeParameterMap abstractInterfaceMethod = method.getConcreteTypeOfParameter(p).findSingleAbstractMethodOfInterface(inspectionProvider);
        log(METHOD_CALL, "Abstract interface method of parameter {} of method {} is {}", p, method.methodInspection.getFullyQualifiedName(), abstractInterfaceMethod);
        if (abstractInterfaceMethod == null || singleAbstractMethod == null) return abstractInterfaceMethod;

        if (singleAbstractMethod.isSingleAbstractMethod() && singleAbstractMethod.methodInspection.getMethodInfo().typeInfo
                .equals(method.methodInspection.getMethodInfo().typeInfo)) {
            Map<NamedType, ParameterizedType> links = new HashMap<>();
            int pos = 0;
            for (TypeParameter key : singleAbstractMethod.methodInspection.getMethodInfo().typeInfo.typeInspection.get().typeParameters()) {
                NamedType abstractTypeInCandidate = method.methodInspection.getMethodInfo().methodInspection.get().getReturnType().parameters.get(pos).typeParameter;
                ParameterizedType valueOfKey = singleAbstractMethod.applyMap(key);
                links.put(abstractTypeInCandidate, valueOfKey);
                pos++;
            }
            return abstractInterfaceMethod.expand(links);
        }
        // Map<NamedType, ParameterizedType> newMap = makeTranslationMap(method, p, singleAbstractMethod);
        // return new MethodTypeParameterMap(abstractInterfaceMethod.methodInfo, abstractInterfaceMethod.expand(newMap).concreteTypes);
        return abstractInterfaceMethod;
    }

}
