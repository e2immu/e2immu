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

package org.e2immu.analyser.inspector.expr;

import com.github.javaparser.Position;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.model.IsAssignableFrom.Mode.COVARIANT_ERASURE;
import static org.e2immu.analyser.model.IsAssignableFrom.NOT_ASSIGNABLE;
import static org.e2immu.analyser.util.Logger.LogTarget.METHOD_CALL;
import static org.e2immu.analyser.util.Logger.log;

public record ParseMethodCallExpr(TypeContext typeContext) {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseMethodCallExpr.class);

    public Expression erasure(ExpressionContext expressionContext, MethodCallExpr methodCallExpr) {
        String methodName = methodCallExpr.getName().asString();
        int numArguments = methodCallExpr.getArguments().size();
        log(METHOD_CALL, "Start computing erasure of method call {}, method name {}, {} args", methodCallExpr,
                methodName, numArguments);

        Scope scope = Scope.computeScope(expressionContext, typeContext, methodCallExpr, TypeParameterMap.EMPTY);
        List<TypeContext.MethodCandidate> methodCandidates = initialMethodCandidates(scope, numArguments, methodName);

        FilterResult filterResult = filterMethodCandidatesInErasureMode(expressionContext, methodCandidates,
                methodCallExpr.getArguments());
        if (methodCandidates.size() > 1) {
            trimMethodsWithBestScore(methodCandidates, filterResult.compatibilityScore);
            if (methodCandidates.size() > 1) {
                trimVarargsVsMethodsWithFewerParameters(methodCandidates);
            }
        }
        sortRemainingCandidatesByShallowPublic(methodCandidates);

        Set<ParameterizedType> types = methodCandidates.stream()
                .map(mc -> {
                    TypeParameterMap map = filterResult.typeParameterMap(typeContext, mc.method().methodInspection)
                            .merge(scope.typeParameterMap());
                    ParameterizedType returnType = mc.method().methodInspection.getReturnType();
                    return returnType.applyTranslation(map.map());
                })
                .collect(Collectors.toUnmodifiableSet());
        log(METHOD_CALL, "Erasure types: {}", types);
        return new MethodCallErasure(types, methodName);
    }

    public Expression parse(ExpressionContext expressionContext,
                            MethodCallExpr methodCallExpr,
                            ForwardReturnTypeInfo forwardReturnTypeInfo) {
        String methodName = methodCallExpr.getName().asString();
        int numArguments = methodCallExpr.getArguments().size();
        log(METHOD_CALL, "Start parsing method call {}, method name {}, {} args, fwd {}", methodCallExpr,
                methodName, numArguments, forwardReturnTypeInfo.toString(expressionContext.typeContext));

        Scope scope = Scope.computeScope(expressionContext, typeContext, methodCallExpr, forwardReturnTypeInfo.extra());

        ErrorInfo errorInfo = new ErrorInfo("method " + methodName, scope.type(),
                methodCallExpr.getBegin().orElseThrow());

        try {
            List<TypeContext.MethodCandidate> methodCandidates = initialMethodCandidates(scope, numArguments, methodName);

            TypeParameterMap extra = forwardReturnTypeInfo.extra().merge(scope.typeParameterMap());

            Candidate candidate = chooseCandidateAndEvaluateCall(expressionContext,
                    methodCandidates,
                    methodCallExpr.getArguments(),
                    forwardReturnTypeInfo.type(),
                    extra,
                    errorInfo);

            assert candidate != null : "Should have found a unique candidate for " + errorInfo.toString(typeContext);
            log(METHOD_CALL, "Resulting method is {}", candidate.method.methodInspection.getMethodInfo().fullyQualifiedName);

            Expression newScope = scope.ensureExplicit(candidate.method.methodInspection, typeContext, expressionContext);
            ParameterizedType returnType = candidate.returnType(typeContext.getPrimitives());
            log(METHOD_CALL, "Concrete return type of {} is {}", errorInfo.methodName, returnType.detailedString(typeContext));

            return new MethodCall(Identifier.from(methodCallExpr),
                    scope.objectIsImplicit(),
                    newScope,
                    candidate.method.methodInspection.getMethodInfo(),
                    returnType,
                    candidate.newParameterExpressions);
        } catch (Throwable rte) {
            LOGGER.error("Exception at {}, at {}", errorInfo.methodName, errorInfo.position);
            throw rte;
        }
    }

    private List<TypeContext.MethodCandidate> initialMethodCandidates(Scope scope,
                                                                      int numArguments,
                                                                      String methodName) {
        List<TypeContext.MethodCandidate> methodCandidates = new ArrayList<>();
        typeContext.recursivelyResolveOverloadedMethods(scope.type(), methodName,
                numArguments, false, scope.typeParameterMap().map(), methodCandidates,
                scope.nature());
        assert !methodCandidates.isEmpty() : "No candidates at all for method name " + methodName + ", "
                + numArguments + " args in type " + scope.type().detailedString(typeContext);
        return methodCandidates;
    }

    record ErrorInfo(String methodName, ParameterizedType type, Position position) {
        public String toString(InspectionProvider inspectionProvider) {
            return "[methodName='" + methodName + '\'' +
                    ", type=" + type.detailedString(inspectionProvider) +
                    ", position=" + position +
                    ']';
        }
    }

    /**
     * Return object of the main selection method <code>chooseCandidateAndEvaluateCall</code>.
     *
     * @param newParameterExpressions parameter expressions, now evaluated in the "correct" context
     * @param mapExpansion            Type parameters involving the method call receive their concrete value.
     *                                Examples:
     *                                MethodCall_0 forEach(lambda) maps T as #0 in Consumer to Type MethodCall_0.Get.
     *                                MethodCall_4 copy(string list) maps E as #0 in List to Type String.
     * @param method                  the candidate, consisting of a MethodInfo object, and a type map
     */
    record Candidate(List<Expression> newParameterExpressions,
                     Map<NamedType, ParameterizedType> mapExpansion,
                     MethodTypeParameterMap method) {

        ParameterizedType returnType(Primitives primitives) {
            return mapExpansion.isEmpty()
                    ? method.getConcreteReturnType(primitives)
                    : method.expand(mapExpansion).getConcreteReturnType(primitives);
        }
    }

    Candidate chooseCandidateAndEvaluateCall(ExpressionContext expressionContext,
                                             List<TypeContext.MethodCandidate> methodCandidates,
                                             List<com.github.javaparser.ast.expr.Expression> expressions,
                                             ParameterizedType returnType,
                                             TypeParameterMap extra,
                                             ErrorInfo errorInfo) {

        Map<Integer, Expression> evaluatedExpressions = new TreeMap<>();
        int i = 0;
        ForwardReturnTypeInfo forward = new ForwardReturnTypeInfo(null, true);
        for (com.github.javaparser.ast.expr.Expression expr : expressions) {
            Expression evaluated = expressionContext.parseExpression(expr, forward);
            evaluatedExpressions.put(i++, evaluated);
        }

        FilterResult filterResult = filterCandidatesByParameters(methodCandidates, evaluatedExpressions, extra);

        // now we need to ensure that there is only 1 method left, but, there can be overloads and
        // methods with implicit type conversions, varargs, etc. etc.
        if (methodCandidates.isEmpty()) {
            return noCandidatesError(errorInfo, filterResult.evaluatedExpressions);
        }

        if (methodCandidates.size() > 1) {
            trimMethodsWithBestScore(methodCandidates, filterResult.compatibilityScore);
            if (methodCandidates.size() > 1) {
                trimVarargsVsMethodsWithFewerParameters(methodCandidates);
            }
        }
        boolean twoAccessible = sortRemainingCandidatesByShallowPublic(methodCandidates);
        if (twoAccessible) {
            multipleCandidatesError(errorInfo, methodCandidates);
        }
        MethodTypeParameterMap method = methodCandidates.get(0).method();
        log(METHOD_CALL, "Found method {}", method.methodInspection.getFullyQualifiedName());


        List<Expression> newParameterExpressions = reEvaluateErasedExpression(expressionContext, expressions,
                returnType, extra, errorInfo, filterResult.evaluatedExpressions, method);
        Map<NamedType, ParameterizedType> mapExpansion = computeMapExpansion(method, newParameterExpressions);
        return new Candidate(newParameterExpressions, mapExpansion, method);
    }

    private void multipleCandidatesError(ErrorInfo errorInfo, List<TypeContext.MethodCandidate> methodCandidates) {
        LOGGER.error("Multiple candidates for {}", errorInfo);
        methodCandidates.forEach(mc -> LOGGER.error(" -- {}", mc.method().methodInspection.getMethodInfo().fullyQualifiedName));
        throw new UnsupportedOperationException("Multiple candidates");
    }

    private Map<NamedType, ParameterizedType> computeMapExpansion(MethodTypeParameterMap method,
                                                                  List<Expression> newParameterExpressions) {
        Map<NamedType, ParameterizedType> mapExpansion = new HashMap<>();
        // fill in the map expansion, deal with variable arguments!
        int i = 0;
        List<ParameterInfo> formalParameters = method.methodInspection.getParameters();

        for (Expression expression : newParameterExpressions) {
            log(METHOD_CALL, "Examine parameter {}", i);
            ParameterizedType concreteParameterType = expression.returnType();
            ParameterInfo formalParameter = formalParameters.get(i);
            ParameterizedType formalParameterType =
                    formalParameters.size() - 1 == i &&
                            formalParameter.parameterInspection.get().isVarArgs() ?
                            formalParameter.parameterizedType.copyWithOneFewerArrays() :
                            formalParameters.get(i).parameterizedType;

            Map<NamedType, ParameterizedType> translated = formalParameterType
                    .translateMap(typeContext, concreteParameterType, true);
            ParameterizedType concreteTypeInMethod = method.getConcreteTypeOfParameter(typeContext.getPrimitives(), i);

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
                /* Example: Ecoll -> String, in case the formal parameter was Collection<E>, and the concrete Set<String>
                Now if Ecoll is a method parameter, it needs linking to the

                 */
                if (!mapExpansion.containsKey(k)) {
                    mapExpansion.put(k, valueToAdd);
                }
            });
            i++;
            if (i >= formalParameters.size()) break; // varargs... we have more than there are
        }
        return mapExpansion;
    }

    private List<Expression> reEvaluateErasedExpression(ExpressionContext expressionContext,
                                                        List<com.github.javaparser.ast.expr.Expression> expressions,
                                                        ParameterizedType outsideContext,
                                                        TypeParameterMap extra,
                                                        ErrorInfo errorInfo,
                                                        Map<Integer, Expression> evaluatedExpressions,
                                                        MethodTypeParameterMap method) {
        List<Expression> newParameterExpressions = new ArrayList<>();
        for (int i = 0; i < expressions.size(); i++) {
            Expression e = evaluatedExpressions.get(i);
            assert e != null;
            if (e.containsErasedExpressions()) {
                log(METHOD_CALL, "Reevaluating erased expression on {}, pos {}", errorInfo.methodName, i);
                ForwardReturnTypeInfo newForward = determineForwardReturnTypeInfo(method, i, outsideContext, extra);

                Expression reParsed = expressionContext.parseExpression(expressions.get(i), newForward);
                assert !reParsed.containsErasedExpressions();
                newParameterExpressions.add(reParsed);
            } else {
                newParameterExpressions.add(e);
            }
        }
        return newParameterExpressions;
    }

    private Candidate noCandidatesError(ErrorInfo errorInfo, Map<Integer, Expression> evaluatedExpressions) {
        LOGGER.error("Evaluated expressions for {} at {}: ", errorInfo.methodName, errorInfo.position);
        evaluatedExpressions.forEach((i, expr) ->
                LOGGER.error("  {} = {}", i, (expr == null ? null : expr.debugOutput())));
        LOGGER.error("No candidate found for {} in type {} at position {}", errorInfo.methodName,
                errorInfo.type.detailedString(typeContext), errorInfo.position);
        return null;
    }

    // FIXME check that this method doesn't do exactly the same as mapExpansion

    private record FilterResult(Map<Integer, Expression> evaluatedExpressions,
                                Map<MethodInfo, Integer> compatibilityScore) {

        public TypeParameterMap typeParameterMap(TypeContext typeContext,
                                                 MethodInspection candidate) {
            Map<NamedType, ParameterizedType> result = new HashMap<>();
            int i = 0;
            for (ParameterInfo parameterInfo : candidate.getParameters()) {
                if (i >= evaluatedExpressions.size()) break;
                Expression expression = evaluatedExpressions.get(i);
                // we have a match between the return type of the expression, and the type of the parameter
                // expression: MethodCallErasure, with Collector<T,?,Set<T>> as concrete type
                // parameter type: Collector<? super T,A,R>   (T belongs to Stream, A,R to the collect method)
                // we want to add R --> Set<T> to the type map
                Map<NamedType, ParameterizedType> map = new HashMap<>();
                if (expression.containsErasedExpressions()) {
                    for (ParameterizedType pt : expression.erasureTypes(typeContext).keySet()) {
                        map.putAll(pt.initialTypeParameterMap(typeContext));
                    }
                } else {
                    map.putAll(expression.returnType().initialTypeParameterMap(typeContext));
                }
                // we now have R as #2 in Collector mapped to Set<T>, and we need to replace that by the
                // actual type parameter of the formal type of parameterInfo
                //result.putAll( parameterInfo.parameterizedType.translateMap(typeContext, map));
                int j = 0;
                for (ParameterizedType tp : parameterInfo.parameterizedType.parameters) {
                    if (tp.typeParameter != null) {
                        int index = j;
                        map.entrySet().stream()
                                .filter(e -> e.getKey() instanceof TypeParameter t && t.getIndex() == index)
                                .map(Map.Entry::getValue)
                                .findFirst()
                                .ifPresent(inMap -> result.put(tp.typeParameter, inMap));
                    }
                    j++;
                }
                i++;
            }
            return new TypeParameterMap(result);
        }
    }


    private FilterResult filterCandidatesByParameters(List<TypeContext.MethodCandidate> methodCandidates,
                                                      Map<Integer, Expression> evaluatedExpressions,
                                                      TypeParameterMap typeParameterMap) {
        Map<Integer, Map<ParameterizedType, ErasureExpression.MethodStatic>> acceptedErasedTypes =
                evaluatedExpressions.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e ->
                        typeParameterMap.replaceKeys(e.getValue().erasureTypes(typeContext))));

        Map<Integer, ParameterizedType> acceptedErasedTypesCombination = null;
        Map<MethodInfo, Integer> compatibilityScore = new HashMap<>();

        for (TypeContext.MethodCandidate methodCandidate : methodCandidates) {
            int sumScore = 0;
            boolean foundCombination = true;
            List<ParameterInfo> parameters = methodCandidate.method().methodInspection.getParameters();
            int pos = 0;
            Map<Integer, ParameterizedType> thisAcceptedErasedTypesCombination = new TreeMap<>();
            for (ParameterInfo parameterInfo : parameters) {

                Map<ParameterizedType, ErasureExpression.MethodStatic> acceptedErased = acceptedErasedTypes.get(pos);
                ParameterizedType bestAcceptedType = null;
                int bestCompatible = Integer.MIN_VALUE;

                ParameterizedType formalType;
                if (parameterInfo.parameterInspection.get().isVarArgs()) {
                    if (acceptedErased == null) {
                        // the parameter is a varargs, and we have the empty array
                        assert parameters.size() == evaluatedExpressions.size() + 1;
                        break;
                    }
                    if (pos == parameters.size() - 1) {
                        // this one can be either the array matching the type, an element of the array
                        ParameterizedType arrayType = parameterInfo.parameterizedType;

                        // here comes a bit of code duplication...
                        for (Map.Entry<ParameterizedType, ErasureExpression.MethodStatic> e : acceptedErased.entrySet()) {
                            ParameterizedType actualType = e.getKey();
                           // ErasureExpression.MethodStatic methodStatic = e.getValue();
                          //  if (methodStatic.test(methodCandidate.method().methodInspection)) {
                                int compatible = callIsAssignableFrom(actualType, arrayType);

                                if (compatible >= 0 && (bestCompatible == Integer.MIN_VALUE || compatible < bestCompatible)) {
                                    bestCompatible = compatible;
                                    bestAcceptedType = actualType;
                                }
                          //  }
                        }

                        // and break off the code duplication, because we cannot set foundCombination to false
                        if (bestCompatible >= 0) {
                            sumScore += bestCompatible;
                            thisAcceptedErasedTypesCombination.put(pos, bestAcceptedType);
                            break;
                        } // else: we have another one to try!
                    }
                    formalType = parameterInfo.parameterizedType.copyWithOneFewerArrays();
                } else {
                    assert acceptedErased != null;
                    formalType = parameterInfo.parameterizedType;
                }


                for (Map.Entry<ParameterizedType, ErasureExpression.MethodStatic> e : acceptedErased.entrySet()) {
                    ParameterizedType actualType = e.getKey();
                    ErasureExpression.MethodStatic methodStatic = e.getValue();
                  //  if (methodStatic != Expression.MethodStatic.YES) {
                        int compatible;

                        if (isFreeTypeParameter(actualType)) {
                            /*
                            See test Lambda_6, and Lambda_7

                            situation: the formal type is a normal TypeInfo, the actual type is a method type parameter
                            representing the method result; we should add the actual<-formal to the extra when evaluating.

                            Lambda_7 shows that we have to be very careful to get rid of type parameters to ensure that
                            this condition doesn't occur too often
                             */
                            compatible = 5;   // FIXME should we actually forward the actual <- formal mapping?
                        } else {
                            compatible = callIsAssignableFrom(actualType, formalType);
                        }
                        if (compatible >= 0 && (bestCompatible == Integer.MIN_VALUE || compatible < bestCompatible)) {
                            bestCompatible = compatible;
                            bestAcceptedType = actualType;
                        }
                   // } // else skip
                }
                if (bestCompatible < 0) {
                    foundCombination = false;
                } else {
                    sumScore += bestCompatible;
                    thisAcceptedErasedTypesCombination.put(pos, bestAcceptedType);
                }
                pos++;
            }
            if (!foundCombination) {
                sumScore = -1; // to be removed immediately
            } else {
                sumScore += IsAssignableFrom.IN_HIERARCHY * methodCandidate.distance();
                if (acceptedErasedTypesCombination == null) {
                    acceptedErasedTypesCombination = thisAcceptedErasedTypesCombination;
                } else if (!acceptedErasedTypesCombination.equals(thisAcceptedErasedTypesCombination)) {
                    log(METHOD_CALL, "Looks like multiple, different, combinations? {} to {}", acceptedErasedTypesCombination,
                            thisAcceptedErasedTypesCombination);
                }
            }
            compatibilityScore.put(methodCandidate.method().methodInspection.getMethodInfo(), sumScore);
        }

        // remove those with a negative compatibility score
        methodCandidates.removeIf(mc -> {
            int score = compatibilityScore.get(mc.method().methodInspection.getMethodInfo());
            return score < 0;
        });

        return new FilterResult(evaluatedExpressions, compatibilityScore);
    }

    private boolean isFreeTypeParameter(ParameterizedType actualType) {
        return actualType.typeParameter != null && actualType.typeParameter.isMethodTypeParameter();
    }

    private FilterResult filterMethodCandidatesInErasureMode(ExpressionContext expressionContext,
                                                             List<TypeContext.MethodCandidate> methodCandidates,
                                                             List<com.github.javaparser.ast.expr.Expression> expressions) {
        Map<Integer, Expression> evaluatedExpressions = new HashMap<>();
        Map<MethodInfo, Integer> compatibilityScore = new HashMap<>();
        while (!methodCandidates.isEmpty() && evaluatedExpressions.size() < expressions.size()) {
            NextParameter next = nextParameterErasureMode(expressionContext, methodCandidates, expressions, evaluatedExpressions);
            if (next == null) break;// we give up, becomes too complicated
            evaluatedExpressions.put(next.pos, Objects.requireNonNull(next.expression));
            filterCandidatesByParameter(next, methodCandidates, compatibilityScore);
        }
        return new FilterResult(evaluatedExpressions, compatibilityScore);
    }

    private record NextParameter(Expression expression, int pos) {
    }

    private NextParameter nextParameterErasureMode(ExpressionContext expressionContext,
                                                   List<TypeContext.MethodCandidate> methodCandidates,
                                                   List<com.github.javaparser.ast.expr.Expression> expressions,
                                                   Map<Integer, Expression> evaluatedExpressions) {
        // we know that all method candidates have an identical amount of parameters (or varargs)
        Set<Integer> ignore = evaluatedExpressions.keySet();
        Integer pos = nextParameterWithoutFunctionalInterfaceTypeOnAnyMethodCandidate(methodCandidates, ignore);
        if (pos == null) {
            return null; // give up
        }
        ForwardReturnTypeInfo forward = new ForwardReturnTypeInfo(null, true);
        Expression evaluatedExpression = expressionContext.parseExpression(expressions.get(pos), forward);
        return new NextParameter(evaluatedExpression, pos);
    }

    /**
     * StringBuilder.length() is in public interface CharSequence and in the private type AbstractStringBuilder.
     * We prioritise the CharSequence version, because that one can be annotated using annotated APIs.
     *
     * @param methodCandidates the candidates to sort
     * @return true when also candidate 1 is accessible... this will result in an error?
     */
    private boolean sortRemainingCandidatesByShallowPublic(List<TypeContext.MethodCandidate> methodCandidates) {
        if (methodCandidates.size() > 1) {
            Comparator<TypeContext.MethodCandidate> comparator =
                    (m1, m2) -> {
                        boolean m1Accessible = m1.method().methodInspection.getMethodInfo().analysisAccessible(typeContext);
                        boolean m2Accessible = m2.method().methodInspection.getMethodInfo().analysisAccessible(typeContext);
                        if (m1Accessible && !m2Accessible) return -1;
                        if (m2Accessible && !m1Accessible) return 1;
                        return 0; // don't know what to prioritize
                    };
            methodCandidates.sort(comparator);
            TypeContext.MethodCandidate m1 = methodCandidates.get(1);
            return m1.method().methodInspection.getMethodInfo().analysisAccessible(typeContext);
        }
        // not two accessible
        return false;
    }

    // FIXME work here!


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


    /**
     * Build the correct ForwardReturnTypeInfo to properly evaluate the argument at position i
     *
     * @param method         the method candidate that won the selection, so that the formal parameter type can be determined
     * @param i              the position of the argument
     * @param outsideContext contextual information to be merged with the formal parameter type
     * @param extra          information about type parameters gathered earlier
     * @return the contextual information merged with the formal parameter type info, so that evaluation can start
     */
    private ForwardReturnTypeInfo determineForwardReturnTypeInfo(MethodTypeParameterMap method,
                                                                 int i,
                                                                 ParameterizedType outsideContext,
                                                                 TypeParameterMap extra) {
        Objects.requireNonNull(method);
        ParameterizedType parameterType = method.getConcreteTypeOfParameter(typeContext.getPrimitives(), i);
        if (outsideContext == null || Primitives.isVoid(outsideContext) || outsideContext.typeInfo == null) {
            // Cannot do better than parameter type, have no outside context;
            return new ForwardReturnTypeInfo(parameterType, false, extra);
        }
        Set<TypeParameter> typeParameters = parameterType.extractTypeParameters();
        Map<NamedType, ParameterizedType> outsideMap = outsideContext.initialTypeParameterMap(typeContext);
        if (typeParameters.isEmpty() || outsideMap.isEmpty()) {
            // No type parameters to fill in or to extract
            return new ForwardReturnTypeInfo(parameterType, false, extra);
        }
        Map<NamedType, ParameterizedType> translate = new HashMap<>();
        for (TypeParameter typeParameter : typeParameters) {
            // can we match? if both are functional interfaces, we know exactly which parameter to match

            // otherwise, we're in a bit of a bind -- they need not necessarily agree
            // List.of(E) --> return is List<E>
            ParameterizedType inMap = outsideMap.get(typeParameter);
            if (inMap != null) {
                translate.put(typeParameter, inMap);
            } else if (typeParameter.isMethodTypeParameter()) {
                // return type is List<E> where E is the method type param; need to match to the type's type param
                TypeParameter typeTypeParameter = tryToFindTypeTypeParameter(method, typeParameter);
                if (typeTypeParameter != null) {
                    ParameterizedType inMap2 = outsideMap.get(typeTypeParameter);
                    if (inMap2 != null) {
                        translate.put(typeParameter, inMap2);
                    }
                }
            }
        }
        if (translate.isEmpty()) {
            // Nothing to translate
            return new ForwardReturnTypeInfo(parameterType, false, extra);
        }
        ParameterizedType translated = parameterType.applyTranslation(translate);
        // Translated context and parameter
        return new ForwardReturnTypeInfo(translated, false, extra);

        /*
        ParameterizedType abstractType = method.methodInspection.formalParameterType(i);
        return new ForwardReturnTypeInfo(abstractType, false);

        if (abstractInterfaceMethod != null) {
            assert singleAbstractMethod != null;
            ParameterizedType returnTypeOfMethod = method.methodInspection.getReturnType();
            assert returnTypeOfMethod.typeInfo != null;
            // a type with its own formal parameters, we try to jump from the concrete value in the AIM (entry.getValue) to
            // the type parameter in the method's return type (tpInReturnType) to the type parameter in the method's formal return type (tpInFormalReturnType)
            // to the value in the SAM (best)
            ParameterizedType formalReturnTypeOfMethod = returnTypeOfMethod.typeInfo.asParameterizedType(typeContext);
            // we should expect type parameters of the return type to be present in the SAM; the ones of the formal type are in the AIM
            Map<NamedType, ParameterizedType> newMap = new HashMap<>();

            MethodTypeParameterMap sam = abstractInterfaceMethod.computeSAM(typeContext);
            assert sam != null;
            for (Map.Entry<NamedType, ParameterizedType> entry : sam.concreteTypes.entrySet()) {
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
            MethodTypeParameterMap map = new MethodTypeParameterMap(sam.methodInspection, newMap);
            return new ForwardReturnTypeInfo(map.applyMap(abstractType), false);
        }
        return new ForwardReturnTypeInfo(singleAbstractMethod.applyMap(abstractType), false);*/
    }


    private TypeParameter tryToFindTypeTypeParameter(MethodTypeParameterMap method, TypeParameter methodTypeParameter) {
        ParameterizedType formalReturnType = method.methodInspection.getReturnType();
        Map<NamedType, ParameterizedType> map = formalReturnType.initialTypeParameterMap(typeContext);
        // map points from E as 0 in List to E as 0 in List.of()
        return map.entrySet().stream().filter(e -> methodTypeParameter.equals(e.getValue().typeParameter))
                .map(e -> (TypeParameter) e.getKey()).findFirst().orElse(null);
    }

        /*
        MethodTypeParameterMap abstractInterfaceMethod
                = parameterType.findSingleAbstractMethodOfInterface(typeContext);
        log(METHOD_CALL, "Abstract interface method of parameter {} of method {} is {}",
                p, method.methodInspection.getFullyQualifiedName(), abstractInterfaceMethod);

        if (abstractInterfaceMethod == null) return new ForwardReturnTypeInfo(parameterType, false);
        MethodTypeParameterMap singleAbstractMethod = outsideContext.findSingleAbstractMethodOfInterface(typeContext);
        if (singleAbstractMethod != null && singleAbstractMethod.isSingleAbstractMethod()
                && singleAbstractMethod.methodInspection.getMethodInfo().typeInfo
                .equals(method.methodInspection.getMethodInfo().typeInfo)) {
            Map<NamedType, ParameterizedType> links = new HashMap<>();
            int pos = 0;
            TypeInspection typeInspection = typeContext.getTypeInspection(singleAbstractMethod
                    .methodInspection.getMethodInfo().typeInfo);
            for (TypeParameter key : typeInspection.typeParameters()) {
                MethodInspection methodInspection = typeContext.getMethodInspection(method
                        .methodInspection.getMethodInfo());
                NamedType abstractTypeInCandidate = methodInspection.getReturnType().parameters.get(pos).typeParameter;
                ParameterizedType valueOfKey = singleAbstractMethod.applyMap(key);
                links.put(abstractTypeInCandidate, valueOfKey);
                pos++;
            }
            MethodTypeParameterMap newSam = abstractInterfaceMethod.expand(links);
            return new ForwardReturnTypeInfo(parameterType, false);
        }
        //abstractInterfaceMethod
        return new ForwardReturnTypeInfo(parameterType, false);
    }
*/

    /*
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
    */
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

    /**
     * This is the "basic" method to quickly filter method candidates in erasure mode.
     * <p>
     * It searches for a parameter position that does not hold a functional interface on
     * any of the method candidates.
     * This method will return position 0 when ignore is empty, and the two method candidates are, for example,
     * StringBuilder.append(String) and StringBuilder.append(Integer). Position 0 does not hold a functional
     * interface on either candidate.
     *
     * @param methodCandidates the remaining candidates
     * @param alreadyDone      the positions already taken
     * @return a new position, or null when we cannot find one
     */
    private Integer nextParameterWithoutFunctionalInterfaceTypeOnAnyMethodCandidate(
            List<TypeContext.MethodCandidate> methodCandidates, Set<Integer> alreadyDone) {
        if (methodCandidates.isEmpty()) return null;
        MethodInspection mi0 = methodCandidates.get(0).method().methodInspection;
        for (int i = 0; i < mi0.getParameters().size(); i++) {
            if (!alreadyDone.contains(i)) {
                boolean ok = true;
                for (TypeContext.MethodCandidate mc : methodCandidates) {
                    MethodInspection mi = mc.method().methodInspection;
                    if (i < mi.getParameters().size()) {
                        ParameterInfo pi = mi.getParameters().get(i);
                        boolean isFunctionalInterface = pi.parameterizedType.isFunctionalInterface(typeContext);
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

    private void filterCandidatesByParameter(NextParameter next,
                                             List<TypeContext.MethodCandidate> methodCandidates,
                                             Map<MethodInfo, Integer> compatibilityScore) {
        methodCandidates.removeIf(mc -> {
            int score = compatibleParameter(next.expression, next.pos, mc.method().methodInspection);
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
    private int compatibleParameter(Expression expression, int pos, MethodInspection methodInspection) {
        assert expression != EmptyExpression.EMPTY_EXPRESSION : "Should we return NOT_ASSIGNABLE?";
        List<ParameterInfo> params = methodInspection.getParameters();

        if (pos >= params.size()) {
            ParameterInfo lastParameter = params.get(params.size() - 1);
            assert lastParameter.parameterInspection.get().isVarArgs();
            ParameterizedType typeOfParameter = lastParameter.parameterizedType.copyWithOneFewerArrays();
            return compatibleParameter(expression, typeOfParameter);
        }
        ParameterInfo parameterInfo = params.get(pos);
        if (pos == params.size() - 1 && parameterInfo.parameterInspection.get().isVarArgs()) {
            int withArrays = compatibleParameter(expression, parameterInfo.parameterizedType);
            int withoutArrays = compatibleParameter(expression, parameterInfo.parameterizedType.copyWithOneFewerArrays());
            if (withArrays == -1) return withoutArrays;
            if (withoutArrays == -1) return withArrays;
            return Math.min(withArrays, withoutArrays);
        }
        // the normal situation
        return compatibleParameter(expression, parameterInfo.parameterizedType);
    }

    private int compatibleParameter(Expression evaluatedExpression, ParameterizedType typeOfParameter) {
        if (evaluatedExpression.containsErasedExpressions()) {
            Map<ParameterizedType, ErasureExpression.MethodStatic> types = evaluatedExpression.erasureTypes(typeContext);
            return types.keySet().stream().mapToInt(type -> callIsAssignableFrom(type, typeOfParameter))
                    .min().orElse(NOT_ASSIGNABLE);
        }

        /* If the evaluated expression is a method with type parameters, then these type parameters
         are allowed in a reverse way (expect List<String>, accept List<T> with T a type parameter of the method,
         as long as T <- String).
        */
        return callIsAssignableFrom(evaluatedExpression.returnType(), typeOfParameter);
    }

    private int callIsAssignableFrom(ParameterizedType actualType, ParameterizedType typeOfParameter) {
        return new IsAssignableFrom(typeContext, typeOfParameter, actualType).execute(false, COVARIANT_ERASURE);
    }
}
