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
import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.inspector.ForwardReturnTypeInfo;
import org.e2immu.analyser.inspector.MethodTypeParameterMap;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.e2immu.analyser.model.ParameterizedType.Mode.COVARIANT;
import static org.e2immu.analyser.model.ParameterizedType.NOT_ASSIGNABLE;
import static org.e2immu.analyser.util.Logger.LogTarget.METHOD_CALL;
import static org.e2immu.analyser.util.Logger.log;

public record ParseMethodCallExpr(InspectionProvider inspectionProvider) {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseMethodCallExpr.class);

    public Expression parse(ExpressionContext expressionContext,
                            MethodCallExpr methodCallExpr,
                            ForwardReturnTypeInfo forwardReturnTypeInfo) {
        String methodName = methodCallExpr.getName().asString();
        int numArguments = methodCallExpr.getArguments().size();
        log(METHOD_CALL, "Start parsing method call {}, method name {}, {} args", methodCallExpr,
                methodName, numArguments);

        Scope scope = Scope.computeScope(expressionContext, inspectionProvider, methodCallExpr);
        List<TypeContext.MethodCandidate> methodCandidates = initialMethodCandidates(scope, expressionContext,
                numArguments, methodName);

        MethodTypeParameterMap combinedSingleAbstractMethod = scope.sam(forwardReturnTypeInfo);
        ErrorInfo errorInfo = new ErrorInfo("method " + methodName, scope.type(),
                methodCallExpr.getBegin().orElseThrow());

        Candidate candidate = chooseCandidateAndEvaluateCall(expressionContext,
                methodCandidates,
                methodCallExpr.getArguments(),
                combinedSingleAbstractMethod,
                errorInfo);

        if (candidate == null) {
            log(METHOD_CALL, "Creating unevaluated method call for {} after failing to " +
                    "choose a candidate; this object will be re-evaluated later", methodName);
            return new UnevaluatedMethodCall(methodName, numArguments);
        }

        Expression newScope = scope.newExpression(candidate.method.methodInspection, inspectionProvider, expressionContext);
        return new MethodCall(Identifier.from(methodCallExpr),
                scope.objectIsImplicit(),
                newScope,
                candidate.method.methodInspection.getMethodInfo(),
                candidate.returnType(),
                candidate.newParameterExpressions);
    }

    private List<TypeContext.MethodCandidate> initialMethodCandidates(Scope scope,
                                                                      ExpressionContext expressionContext,
                                                                      int numArguments,
                                                                      String methodName) {
        List<TypeContext.MethodCandidate> methodCandidates = new ArrayList<>();
        expressionContext.typeContext.recursivelyResolveOverloadedMethods(scope.type(), methodName,
                numArguments, false, scope.typeMap(), methodCandidates,
                scope.nature());
        assert !methodCandidates.isEmpty() : "Compilation error!";
        return methodCandidates;
    }

    record ErrorInfo(String methodName, ParameterizedType type, Position position) {
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

        ParameterizedType returnType() {
            return mapExpansion.isEmpty()
                    ? method.getConcreteReturnType()
                    : method.expand(mapExpansion).getConcreteReturnType();
        }
    }

    Candidate chooseCandidateAndEvaluateCall(ExpressionContext expressionContext,
                                             List<TypeContext.MethodCandidate> methodCandidates,
                                             List<com.github.javaparser.ast.expr.Expression> expressions,
                                             MethodTypeParameterMap singleAbstractMethod,
                                             ErrorInfo errorInfo) {


        FilterResult filterResult = filterCandidatesByParameters(expressionContext,
                methodCandidates, expressions, singleAbstractMethod);

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
        sortRemainingCandidatesByShallowPublic(methodCandidates, inspectionProvider);
        MethodTypeParameterMap method = methodCandidates.get(0).method();
        log(METHOD_CALL, "Found method {}", method.methodInspection.getFullyQualifiedName());


        List<Expression> newParameterExpressions = retryUnevaluatedExpressions(
                expressionContext, expressions, singleAbstractMethod, errorInfo,
                filterResult.evaluatedExpressions, method);
        Map<NamedType, ParameterizedType> mapExpansion = computeMapExpansion(method, newParameterExpressions);
        return new Candidate(newParameterExpressions, mapExpansion, method);
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
                    .translateMap(inspectionProvider, concreteParameterType, true);
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

    private List<Expression> retryUnevaluatedExpressions(ExpressionContext expressionContext,
                                                         List<com.github.javaparser.ast.expr.Expression> expressions,
                                                         MethodTypeParameterMap singleAbstractMethod,
                                                         ErrorInfo errorInfo,
                                                         Map<Integer, Expression> evaluatedExpressions,
                                                         MethodTypeParameterMap method) {
        List<Expression> newParameterExpressions = new ArrayList<>();
        for (int i = 0; i < expressions.size(); i++) {
            Expression e = evaluatedExpressions.get(i);
            if (e == null || e instanceof UnevaluatedLambdaExpression
                    || e instanceof UnevaluatedMethodCall || e instanceof UnevaluatedObjectCreation) {
                log(METHOD_CALL, "Reevaluating unevaluated expression on {}, pos {}, single abstract method {}",
                        errorInfo.methodName, i, singleAbstractMethod);
                ForwardReturnTypeInfo newForward = determineMethodTypeParameterMap(method, i, singleAbstractMethod);

                Expression reParsed = expressionContext.parseExpression(expressions.get(i), newForward);
                if (reParsed instanceof UnevaluatedMethodCall || reParsed instanceof UnevaluatedLambdaExpression) {
                    throw new UnsupportedOperationException("Reevaluation of " + errorInfo.methodName +
                            " fails, have " + reParsed.debugOutput());
                }
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
                errorInfo.type.detailedString(), errorInfo.position);
        return null;
    }

    private record FilterResult(Map<Integer, Expression> evaluatedExpressions,
                                Map<MethodInfo, Integer> compatibilityScore) {

    }

    private FilterResult filterCandidatesByParameters(ExpressionContext expressionContext,
                                                      List<TypeContext.MethodCandidate> methodCandidates,
                                                      List<com.github.javaparser.ast.expr.Expression> expressions,
                                                      MethodTypeParameterMap singleAbstractMethod) {
        Map<Integer, Expression> evaluatedExpressions = new HashMap<>();
        Map<MethodInfo, Integer> compatibilityScore = new HashMap<>();
        while (!methodCandidates.isEmpty() && evaluatedExpressions.size() < expressions.size()) {
            NextParameter next = nextParameter(expressionContext, methodCandidates, expressions,
                    singleAbstractMethod, evaluatedExpressions);
            evaluatedExpressions.put(next.pos, Objects.requireNonNull(next.expression));
            filterCandidatesByParameter(next, methodCandidates, compatibilityScore);
        }
        return new FilterResult(evaluatedExpressions, compatibilityScore);
    }

    private record NextParameter(Expression expression, int pos) {
    }

    /**
     * Compute the next parameter to be used to discriminate between the method candidates.
     * This method dispatches to three different "types" of parameter position selectors.
     *
     * @param expressionContext    the current expression context, needed to evaluate expressions
     * @param methodCandidates     the remaining candidates
     * @param expressions          the JavaParser expressions to be evaluated
     * @param singleAbstractMethod information from the context of this method call
     * @param evaluatedExpressions the map of expressions by position, previously evaluated
     * @return the next parameter position + evaluated expression; never null.
     */
    private NextParameter nextParameter(ExpressionContext expressionContext,
                                        List<TypeContext.MethodCandidate> methodCandidates,
                                        List<com.github.javaparser.ast.expr.Expression> expressions,
                                        MethodTypeParameterMap singleAbstractMethod,
                                        Map<Integer, Expression> evaluatedExpressions) {
        // we know that all method candidates have an identical amount of parameters (or varargs)
        Set<Integer> ignore = evaluatedExpressions.keySet();
        Integer pos = nextParameterWithoutFunctionalInterfaceTypeOnAnyMethodCandidate(methodCandidates, ignore);
        if (pos == null) {
            // so one of the candidates will have a functional interface
            pos = nextParameterWhereUnevaluatedLambdaWillHelp(expressions, methodCandidates, ignore);
            if (pos == null) {
                Pair<MethodTypeParameterMap, Integer> pair = findParameterWithASingleFunctionalInterfaceType(methodCandidates,
                        ignore);
                if (pair != null) {
                    pos = pair.v;
                    ForwardReturnTypeInfo info = determineAbstractInterfaceMethod(pair.k, pos, singleAbstractMethod);
                    return new NextParameter(expressionContext.parseExpression(expressions.get(pos), info), pos);
                } else {
                    // still nothing... just take the next one
                    pos = IntStream.range(0, expressions.size()).filter(i -> !evaluatedExpressions.containsKey(i)).findFirst().orElseThrow();
                }
            }
        }
        /*
         We have a parameter where normal evaluation will help. We must send along the formal type of the parameter in
         the method candidates, as this can influence the evaluation.

         See e.g. MethodCall_5, where GetOnly implements Get; and you cannot write List<GetOnly> l2 = ...; List<Get> list = l2
         because generics are INVARIANT.

         public void accept(List<Get> list) { list.forEach(get -> System.out.println(get.get())); }
         public void accept(Set<Get> set) { set.forEach(get -> System.out.println(get.get())); }

         ... here, List.of(...) becomes a List<Get> because of the context of 'accept(...)'
         accept(List.of(new GetOnly("hello")));

         If we're in the context of a SAM, we send the SAM along
         */

        ParameterizedType type = commonTypeInCandidatesOnParameter(methodCandidates, pos);
        ForwardReturnTypeInfo newForward = new ForwardReturnTypeInfo(type,
                singleAbstractMethod == null ? null : singleAbstractMethod.copyWithoutMethod());
        Expression evaluatedExpression = expressionContext.parseExpression(expressions.get(pos), newForward);
        return new NextParameter(evaluatedExpression, pos);
    }


    /*
    StringBuilder.length is in public interface CharSequence and private type AbstractStringBuilder
    We prioritise the CharSequence version, because that one can be annotated using annotated APIs.
     */

    private void sortRemainingCandidatesByShallowPublic(List<TypeContext.MethodCandidate> methodCandidates,
                                                        InspectionProvider inspectionProvider) {
        Comparator<TypeContext.MethodCandidate> comparator =
                (m1, m2) -> {
                    boolean m1Accessible = m1.method().methodInspection.getMethodInfo().analysisAccessible(inspectionProvider);
                    boolean m2Accessible = m2.method().methodInspection.getMethodInfo().analysisAccessible(inspectionProvider);
                    if (m1Accessible && !m2Accessible) return -1;
                    if (m2Accessible && !m1Accessible) return 1;
                    return 0; // don't know what to prioritize
                };
        methodCandidates.sort(comparator);
    }

    private ParameterizedType commonTypeInCandidatesOnParameter(List<TypeContext.MethodCandidate> methodCandidates, int pos) {
        return methodCandidates.stream().map(mc -> mc.method().parameterizedType(pos))
                .reduce(null, (t1, t2) -> t1 == null ? t2 : t1.commonType(inspectionProvider, t2));
    }

    private ForwardReturnTypeInfo determineMethodTypeParameterMap(MethodTypeParameterMap method,
                                                                  int i,
                                                                  MethodTypeParameterMap singleAbstractMethod) {
        ForwardReturnTypeInfo abstractInterfaceMethod = determineAbstractInterfaceMethod(method, i, singleAbstractMethod);
        ParameterizedType abstractType = method.methodInspection.formalParameterType(i);
        if (abstractInterfaceMethod != null) {
            assert singleAbstractMethod != null;
            ParameterizedType returnTypeOfMethod = method.methodInspection.getReturnType();
            assert returnTypeOfMethod.typeInfo != null;
            // a type with its own formal parameters, we try to jump from the concrete value in the AIM (entry.getValue) to
            // the type parameter in the method's return type (tpInReturnType) to the type parameter in the method's formal return type (tpInFormalReturnType)
            // to the value in the SAM (best)
            ParameterizedType formalReturnTypeOfMethod = returnTypeOfMethod.typeInfo.asParameterizedType(inspectionProvider);
            // we should expect type parameters of the return type to be present in the SAM; the ones of the formal type are in the AIM
            Map<NamedType, ParameterizedType> newMap = new HashMap<>();
            for (Map.Entry<NamedType, ParameterizedType> entry : abstractInterfaceMethod.sam().concreteTypes.entrySet()) {
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
            MethodTypeParameterMap map = new MethodTypeParameterMap(abstractInterfaceMethod.sam().methodInspection, newMap);
            return new ForwardReturnTypeInfo(map.applyMap(abstractType), map);
        }
        /*
        // could be that e == null, we did not evaluate
        if (e instanceof UnevaluatedMethodCall && singleAbstractMethod != null) {
            Map<NamedType, ParameterizedType> newMap = makeTranslationMap(method, i, singleAbstractMethod);
            return new ForwardReturnTypeInfo(null, new MethodTypeParameterMap(null, newMap));
        }
        if (singleAbstractMethod == null) return new ForwardReturnTypeInfo(abstractType, null);

         */
        return new ForwardReturnTypeInfo(singleAbstractMethod.applyMap(abstractType), singleAbstractMethod);
    }

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
     * File.listFiles(FileNameFilter) vs File.listFiles(FileFilter): both types take a functional interface with
     * a different number of parameters (fileFilter takes 1, fileNameFilter takes 2).
     * In this case, evaluating the expression will work.
     *
     * @param expressions      original JavaParser expressions
     * @param methodCandidates the remaining method candidates
     * @param ignore           the positions used earlier to reduce the method candidate list
     * @return the position containing parameters of functional interface type, or null when this does not exist
     */

    private Integer nextParameterWhereUnevaluatedLambdaWillHelp(
            List<com.github.javaparser.ast.expr.Expression> expressions,
            List<TypeContext.MethodCandidate> methodCandidates,
            Set<Integer> ignore) {
        assert !methodCandidates.isEmpty();
        MethodInspection mi0 = methodCandidates.get(0).method().methodInspection;

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
                        assert added;
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
                for (TypeContext.MethodCandidate mc : methodCandidates) {
                    MethodInspection mi = mc.method().methodInspection;
                    if (i < mi.getParameters().size()) {
                        ParameterInfo pi = mi.getParameters().get(i);
                        boolean isFunctionalInterface = pi.parameterizedType.isFunctionalInterface(inspectionProvider);
                        if (isFunctionalInterface) {
                            assert functionalInterface == null;

                            functionalInterface = pi.parameterizedType;
                            mcOfFunctionalInterface = mc;
                           /* } else {
                                MethodTypeParameterMap sam2 = pi.parameterizedType.findSingleAbstractMethodOfInterface(inspectionProvider);
                                if (!singleAbstractMethod.isAssignableFrom(sam2)) {
                                    log(METHOD_CALL, "Incompatible functional interfaces {} and {} on method overloads {} and {}",
                                            pi.parameterizedType.detailedString(), functionalInterface.detailedString(),
                                            mi.getDistinguishingName(), mcOfFunctionalInterface.method().methodInspection.getDistinguishingName());
                                    functionalInterface = null;
                                    break;
                                }
                            }*/
                        }
                    }
                }
                if (functionalInterface != null) return new Pair<>(mcOfFunctionalInterface.method(), i);
            }
        }
        return null;
    }

    /**
     * This is the "basic" method, which searches for a parameter position that does not hold a functional interface on
     * any of the method candidates.
     * This method will return position 0 when ignore is empty, and the two method candidates are, for example,
     * StringBuilder.append(String) and StringBuilder.append(Integer). Position 0 does not hold a functional
     * interface on either candidate.
     *
     * @param methodCandidates the remaining candidates
     * @param ignore           the positions already taken
     * @return a new position, or null when we cannot find one
     */
    private Integer nextParameterWithoutFunctionalInterfaceTypeOnAnyMethodCandidate(
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
        if (evaluatedExpression instanceof UnevaluatedMethodCall) {
            return 1; // we'll just have to redo this
        }
        if (evaluatedExpression instanceof UnevaluatedLambdaExpression ule) {
            MethodTypeParameterMap sam = typeOfParameter.findSingleAbstractMethodOfInterface(inspectionProvider);
            if (sam == null) return NOT_ASSIGNABLE;
            int numberOfParametersInSam = sam.methodInspection.getParameters().size();
            return ule.numberOfParameters().contains(numberOfParametersInSam) ? 0 : NOT_ASSIGNABLE;
        }

        /* If the evaluated expression is a method with type parameters, then these type parameters
         are allowed in a reverse way (expect List<String>, accept List<T> with T a type parameter of the method,
         as long as T <- String).
        */
        ParameterizedType returnType = evaluatedExpression.returnType();
        Set<TypeParameter> reverseParameters;
        if (evaluatedExpression instanceof MethodCall methodCall) {
            MethodInspection callInspection = inspectionProvider.getMethodInspection(methodCall.methodInfo);
            if (!callInspection.getTypeParameters().isEmpty()) {
                reverseParameters = new HashSet<>(callInspection.getTypeParameters());
            } else {
                reverseParameters = null;
            }
        } else {
            reverseParameters = null;
        }

        if (typeOfParameter.isFunctionalInterface(inspectionProvider) && returnType.isFunctionalInterface(inspectionProvider)) {
            MethodTypeParameterMap sam1 = typeOfParameter.findSingleAbstractMethodOfInterface(inspectionProvider);
            MethodTypeParameterMap sam2 = returnType.findSingleAbstractMethodOfInterface(inspectionProvider);
            return sam1.isAssignableFrom(sam2) ? 0 : NOT_ASSIGNABLE;
        }
        return typeOfParameter.numericIsAssignableFrom(inspectionProvider, returnType,
                false, COVARIANT, reverseParameters);
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
    private ForwardReturnTypeInfo determineAbstractInterfaceMethod(MethodTypeParameterMap method,
                                                                   int p,
                                                                   MethodTypeParameterMap singleAbstractMethod) {
        Objects.requireNonNull(method);
        ParameterizedType parameterType = method.getConcreteTypeOfParameter(p);
        MethodTypeParameterMap abstractInterfaceMethod
                = parameterType.findSingleAbstractMethodOfInterface(inspectionProvider);
        log(METHOD_CALL, "Abstract interface method of parameter {} of method {} is {}",
                p, method.methodInspection.getFullyQualifiedName(), abstractInterfaceMethod);
        if (abstractInterfaceMethod == null) return ForwardReturnTypeInfo.NO_INFO;
        if (singleAbstractMethod != null && singleAbstractMethod.isSingleAbstractMethod()
                && singleAbstractMethod.methodInspection.getMethodInfo().typeInfo
                .equals(method.methodInspection.getMethodInfo().typeInfo)) {
            Map<NamedType, ParameterizedType> links = new HashMap<>();
            int pos = 0;
            TypeInspection typeInspection = inspectionProvider.getTypeInspection(singleAbstractMethod
                    .methodInspection.getMethodInfo().typeInfo);
            for (TypeParameter key : typeInspection.typeParameters()) {
                MethodInspection methodInspection = inspectionProvider.getMethodInspection(method
                        .methodInspection.getMethodInfo());
                NamedType abstractTypeInCandidate = methodInspection.getReturnType().parameters.get(pos).typeParameter;
                ParameterizedType valueOfKey = singleAbstractMethod.applyMap(key);
                links.put(abstractTypeInCandidate, valueOfKey);
                pos++;
            }
            return new ForwardReturnTypeInfo(parameterType, abstractInterfaceMethod.expand(links));
        }
        return new ForwardReturnTypeInfo(parameterType, abstractInterfaceMethod);
    }

}
