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
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.model.IsAssignableFrom.Mode.COVARIANT_ERASURE;
import static org.e2immu.analyser.model.IsAssignableFrom.NOT_ASSIGNABLE;

public record ParseMethodCallExpr(TypeContext typeContext) {

    public ParseMethodCallExpr {
        Objects.requireNonNull(typeContext);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ParseMethodCallExpr.class);

    public Expression erasure(ExpressionContext expressionContext, MethodCallExpr methodCallExpr) {
        String methodName = methodCallExpr.getName().asString();
        int numArguments = methodCallExpr.getArguments().size();
        LOGGER.debug("Start computing erasure of method call {}, method name {}, {} args", methodCallExpr,
                methodName, numArguments);

        Scope scope = Scope.computeScope(expressionContext, typeContext, methodCallExpr, TypeParameterMap.EMPTY);
        Map<MethodTypeParameterMap, Integer> methodCandidates = initialMethodCandidates(scope, numArguments, methodName);

        FilterResult filterResult = filterMethodCandidatesInErasureMode(expressionContext, methodCandidates,
                methodCallExpr.getArguments());
        if (methodCandidates.size() > 1) {
            trimMethodsWithBestScore(methodCandidates, filterResult.compatibilityScore);
            if (methodCandidates.size() > 1) {
                trimVarargsVsMethodsWithFewerParameters(methodCandidates);
            }
        }
        sortRemainingCandidatesByShallowPublic(methodCandidates);

        if (methodCandidates.isEmpty()) {
            throw new RuntimeException("Have no method candidates remaining in erasure mode for "
                    + methodName + ", " + numArguments);
        }

        Set<ParameterizedType> types;
        if (scope.expression() != null
                && !(scope.expression() instanceof ErasureExpression)
                && scope.expression().returnType().arrays > 0
                && "clone".equals(methodName)) {
            /* this condition is hyper-specialized (see MethodCall_54; but the alternative would be to return JLO,
               and that causes problems along the way
             */
            types = Set.of(scope.expression().returnType());
        } else {
            types = methodCandidates.keySet().stream()
                    .map(mc -> erasureReturnType(mc, filterResult, scope))
                    .collect(Collectors.toUnmodifiableSet());
        }
        LOGGER.debug("Erasure types: {}", types);
        return new MethodCallErasure(types, methodName);
    }

    private ParameterizedType erasureReturnType(MethodTypeParameterMap mc, FilterResult filterResult, Scope scope) {
        TypeParameterMap map0 = filterResult.typeParameterMap(typeContext, mc.methodInspection);
        TypeParameterMap map1 = map0.merge(scope.typeParameterMap());
        TypeInfo methodType = mc.methodInspection.getMethodInfo().typeInfo;
        TypeInfo scopeType = scope.type().bestTypeInfo(typeContext);
        TypeParameterMap merged;
        if (scopeType != null && !methodType.equals(scope.type().typeInfo)) {
            // method is defined in a super-type, so we need an additional translation
            ParameterizedType superType = methodType.asParameterizedType(typeContext);
            Map<NamedType, ParameterizedType> sm = scopeType.mapInTermsOfParametersOfSuperType(typeContext, superType);
            merged = sm == null ? map1 : map1.merge(new TypeParameterMap(sm));
        } else {
            merged = map1;
        }
        ParameterizedType returnType = mc.methodInspection.getReturnType();
        Map<NamedType, ParameterizedType> map2 = merged.map();
        // IMPROVE at some point, compare to mc.method().concreteType; redundant code?
        return returnType.applyTranslation(typeContext().getPrimitives(), map2);
    }

    public Expression parse(ExpressionContext expressionContext,
                            MethodCallExpr methodCallExpr,
                            ForwardReturnTypeInfo forwardReturnTypeInfo) {
        String methodName = methodCallExpr.getName().asString();
        int numArguments = methodCallExpr.getArguments().size();
        LOGGER.debug("Start parsing method call {}, method name {}, {} args, fwd {}", methodCallExpr,
                methodName, numArguments, forwardReturnTypeInfo.toString(expressionContext.typeContext()));

        Scope scope = Scope.computeScope(expressionContext, typeContext, methodCallExpr, forwardReturnTypeInfo.extra());

        ErrorInfo errorInfo = new ErrorInfo("method " + methodName, scope.type(),
                methodCallExpr.getBegin().orElseThrow());

        try {
            Map<MethodTypeParameterMap, Integer> methodCandidates = initialMethodCandidates(scope, numArguments, methodName);
            if (methodCandidates.isEmpty()) {
                throw new RuntimeException("Method candidates are empty for " + errorInfo.toString(typeContext));
            }
            TypeParameterMap extra = forwardReturnTypeInfo.extra().merge(scope.typeParameterMap());

            Candidate candidate = chooseCandidateAndEvaluateCall(expressionContext,
                    methodCandidates,
                    methodCallExpr.getArguments(),
                    forwardReturnTypeInfo.type(),
                    extra,
                    errorInfo,
                    false);

            if (candidate == null) {
                tryToExplain(scope, numArguments, methodName, forwardReturnTypeInfo, expressionContext, methodCallExpr);
                throw new RuntimeException("Should have found a unique candidate for " + errorInfo.toString(typeContext));
            }
            LOGGER.debug("Resulting method is {}", candidate.method.methodInspection.getMethodInfo().fullyQualifiedName);

            boolean scopeIsThis = scope.expression() instanceof VariableExpression ve && ve.variable() instanceof This;
            Expression newScope = scope.ensureExplicit(candidate.method.methodInspection,
                    Identifier.from(methodCallExpr), typeContext, scopeIsThis, expressionContext);
            ParameterizedType returnType = candidate.returnType(typeContext, expressionContext.primaryType());
            LOGGER.debug("Concrete return type of {} is {}", errorInfo.methodName, returnType.detailedString(typeContext));

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

    private void tryToExplain(Scope scope, int numArguments, String methodName, ForwardReturnTypeInfo forwardReturnTypeInfo,
                              ExpressionContext expressionContext, MethodCallExpr methodCallExpr) {
        LOGGER.error("******** Try to explain");
        Map<MethodTypeParameterMap, Integer> methodCandidates = initialMethodCandidates(scope, numArguments, methodName);
        LOGGER.error("Have {} candidates", methodCandidates.size());
        TypeParameterMap extra = forwardReturnTypeInfo.extra().merge(scope.typeParameterMap());
        ErrorInfo errorInfo = new ErrorInfo(methodName, scope.type(), methodCallExpr.getBegin().orElseThrow());
        chooseCandidateAndEvaluateCall(expressionContext,
                methodCandidates,
                methodCallExpr.getArguments(),
                forwardReturnTypeInfo.type(),
                extra,
                errorInfo,
                true);
        LOGGER.error("********* End of try to explain");
    }

    private Map<MethodTypeParameterMap, Integer> initialMethodCandidates(Scope scope,
                                                                         int numArguments,
                                                                         String methodName) {
        Map<MethodTypeParameterMap, Integer> methodCandidates = new HashMap<>();
        typeContext.recursivelyResolveOverloadedMethods(scope.type(), methodName,
                numArguments, false, scope.typeParameterMap().map(), methodCandidates,
                scope.nature());
        if (methodCandidates.isEmpty()) {
            throw new RuntimeException("No candidates at all for method name " + methodName + ", "
                    + numArguments + " args in type " + scope.type().detailedString(typeContext));
        }
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

        ParameterizedType returnType(InspectionProvider inspectionProvider, TypeInfo primaryType) {
            Primitives primitives = inspectionProvider.getPrimitives();
            ParameterizedType pt = mapExpansion.isEmpty()
                    ? method.getConcreteReturnType(primitives)
                    : method.expand(inspectionProvider, primaryType, mapExpansion).getConcreteReturnType(primitives);
            // See TypeParameter_4
            return pt.isUnboundWildcard() ? inspectionProvider.getPrimitives().objectParameterizedType() : pt;
        }
    }

    Candidate chooseCandidateAndEvaluateCall(ExpressionContext expressionContext,
                                             Map<MethodTypeParameterMap, Integer> methodCandidates,
                                             List<com.github.javaparser.ast.expr.Expression> expressions,
                                             ParameterizedType returnType,
                                             TypeParameterMap extra,
                                             ErrorInfo errorInfo,
                                             boolean explain) {

        Map<Integer, Expression> evaluatedExpressions = new TreeMap<>();
        int i = 0;
        ForwardReturnTypeInfo forward = new ForwardReturnTypeInfo(null, true);
        for (com.github.javaparser.ast.expr.Expression expr : expressions) {
            Expression evaluated = expressionContext.parseExpression(expr, forward);
            evaluatedExpressions.put(i++, evaluated);
        }

        FilterResult filterResult = filterCandidatesByParameters(methodCandidates, evaluatedExpressions, extra, explain);

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
        List<MethodTypeParameterMap> sorted = sortRemainingCandidatesByShallowPublic(methodCandidates);
        if (sorted.size() > 1) {
            multipleCandidatesError(errorInfo, methodCandidates, filterResult.evaluatedExpressions);
        }
        MethodTypeParameterMap method = sorted.get(0);
        LOGGER.debug("Found method {}", method.methodInspection.getFullyQualifiedName());


        List<Expression> newParameterExpressions = reEvaluateErasedExpression(expressionContext, expressions,
                returnType, extra, errorInfo, filterResult.evaluatedExpressions, method);
        Map<NamedType, ParameterizedType> mapExpansion = computeMapExpansion(method, newParameterExpressions,
                returnType, expressionContext.primaryType());
        return new Candidate(newParameterExpressions, mapExpansion, method);
    }

    private void multipleCandidatesError(ErrorInfo errorInfo,
                                         Map<MethodTypeParameterMap, Integer> methodCandidates,
                                         Map<Integer, Expression> evaluatedExpressions) {
        LOGGER.error("Multiple candidates for {}", errorInfo);
        methodCandidates.forEach((m, d) -> LOGGER.error(" -- {}", m.methodInspection.getMethodInfo().fullyQualifiedName));
        LOGGER.error("{} Evaluated expressions:", evaluatedExpressions.size());
        evaluatedExpressions.forEach((i, e) -> LOGGER.error(" -- index {}: {}, {}, {}", i, e, e.getClass(),
                e instanceof ErasureExpression ? "-" : e.returnType().toString()));
        throw new UnsupportedOperationException("Multiple candidates");
    }

    private Map<NamedType, ParameterizedType> computeMapExpansion(MethodTypeParameterMap method,
                                                                  List<Expression> newParameterExpressions,
                                                                  ParameterizedType forwardedReturnType,
                                                                  TypeInfo primaryType) {
        Map<NamedType, ParameterizedType> mapExpansion = new HashMap<>();
        // fill in the map expansion, deal with variable arguments!
        int i = 0;
        List<ParameterInfo> formalParameters = method.methodInspection.getParameters();

        for (Expression expression : newParameterExpressions) {
            LOGGER.debug("Examine parameter {}", i);
            ParameterizedType concreteParameterType;
            ParameterizedType formalParameterType;
            ParameterInfo formalParameter = formalParameters.get(i);
            if (formalParameters.size() - 1 == i && formalParameter.parameterInspection.get().isVarArgs()) {
                formalParameterType = formalParameter.parameterizedType.copyWithOneFewerArrays();
                if (newParameterExpressions.size() > formalParameters.size()
                        || formalParameterType.arrays == expression.returnType().arrays) {
                    concreteParameterType = expression.returnType();
                } else {
                    concreteParameterType = expression.returnType().copyWithOneFewerArrays();
                    assert formalParameterType.isAssignableFrom(typeContext, concreteParameterType);
                }
            } else {
                formalParameterType = formalParameters.get(i).parameterizedType;
                concreteParameterType = expression.returnType();
            }
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

        // finally, look at the return type
        ParameterizedType formalReturnType = method.methodInspection.getReturnType();
        if (formalReturnType.isTypeParameter() && forwardedReturnType != null) {
            mapExpansion.merge(formalReturnType.typeParameter, forwardedReturnType,
                    (ptOld, ptNew) -> ptOld.mostSpecific(typeContext, primaryType, ptNew));
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
        Expression[] newParameterExpressions = new Expression[evaluatedExpressions.size()];
        TypeParameterMap cumulative = extra;
        List<Integer> positionsToDo = new ArrayList<>(evaluatedExpressions.size());

        for (int i = 0; i < expressions.size(); i++) {
            Expression e = evaluatedExpressions.get(i);
            assert e != null;
            if (e.containsErasedExpressions()) {
                positionsToDo.add(i);
            } else {
                newParameterExpressions[i] = e;
                Map<NamedType, ParameterizedType> learned = e.returnType().initialTypeParameterMap(typeContext);
                List<ParameterInfo> parameters = method.methodInspection.getParameters();
                ParameterizedType formal = i < parameters.size() ? parameters.get(i).parameterizedType :
                        parameters.get(parameters.size() - 1).parameterizedType.copyWithOneFewerArrays();
                Map<NamedType, ParameterizedType> inMethod = formal.forwardTypeParameterMap(typeContext);
                Map<NamedType, ParameterizedType> combined = TypeInfo.combineMaps(learned, inMethod);
                if (!combined.isEmpty()) {
                    cumulative = cumulative.merge(new TypeParameterMap(combined));
                }
                if (formal.typeParameter != null) {
                    Map<NamedType, ParameterizedType> map = Map.of(formal.typeParameter, e.returnType().copyWithoutArrays());
                    cumulative = cumulative.merge(new TypeParameterMap(map));
                }
            }
        }

        for (int i : positionsToDo) {
            Expression e = evaluatedExpressions.get(i);
            assert e != null;

            LOGGER.debug("Reevaluating erased expression on {}, pos {}", errorInfo.methodName, i);
            ForwardReturnTypeInfo newForward = determineForwardReturnTypeInfo(method, i, outsideContext, cumulative);

            Expression reParsed = expressionContext.parseExpression(expressions.get(i), newForward);
            assert !reParsed.containsErasedExpressions();
            newParameterExpressions[i] = reParsed;

            Map<NamedType, ParameterizedType> learned = reParsed.returnType().initialTypeParameterMap(typeContext);
            if (!learned.isEmpty()) {
                cumulative = cumulative.merge(new TypeParameterMap(learned));
            }
            ParameterInfo pi = method.methodInspection.getParameters().get(Math.min(i, method.methodInspection.getParameters().size() - 1));
            if (pi.parameterizedType.hasTypeParameters()) {
                // try to reconcile the type parameters with the ones in reParsed, see Lambda_16
                Map<NamedType, ParameterizedType> forward = pi.parameterizedType.forwardTypeParameterMap(typeContext);
                if (!forward.isEmpty()) {
                    cumulative = cumulative.merge(new TypeParameterMap(forward));
                }
            }
        }
        return Arrays.stream(newParameterExpressions).toList();
    }

    private Candidate noCandidatesError(ErrorInfo errorInfo, Map<Integer, Expression> evaluatedExpressions) {
        LOGGER.error("Evaluated expressions for {} at {}: ", errorInfo.methodName, errorInfo.position);
        evaluatedExpressions.forEach((i, expr) ->
                LOGGER.error("  {} = {}", i, (expr == null ? null : expr.minimalOutput())));
        LOGGER.error("No candidate found for {} in type {} at position {}", errorInfo.methodName,
                errorInfo.type.detailedString(typeContext), errorInfo.position);
        return null;
    }

    private record FilterResult(Map<Integer, Expression> evaluatedExpressions,
                                Map<MethodInfo, Integer> compatibilityScore) {

        // See Lambda_6, Lambda_13: connect type of evaluated argument result to formal parameter type
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
                boolean containsErasedExpressions = expression.containsErasedExpressions();
                if (containsErasedExpressions) {
                    for (ParameterizedType pt : expression.erasureTypes(typeContext)) {
                        map.putAll(pt.initialTypeParameterMap(typeContext));
                    }
                } else {
                    map.putAll(expression.returnType().initialTypeParameterMap(typeContext));
                }
                // we now have R as #2 in Collector mapped to Set<T>, and we need to replace that by the
                // actual type parameter of the formal type of parameterInfo
                //result.putAll( parameterInfo.parameterizedType.translateMap(typeContext, map));
                int j = 0;
                boolean isVarargs = parameterInfo.parameterInspection.get().isVarArgs();
                for (ParameterizedType tp : parameterInfo.parameterizedType.parameters) {
                    if (tp.typeParameter != null) {
                        int index = j;
                        map.entrySet().stream()
                                .filter(e -> e.getKey() instanceof TypeParameter t && t.getIndex() == index)
                                // we do not add to the map when the result is one type parameter to the next (MethodCall_19)
                                .filter(e -> e.getValue().bestTypeInfo(typeContext) != null)
                                .map(Map.Entry::getValue)
                                .findFirst()
                                .ifPresent(inMap -> {
                                    // see MethodCall_60,_61,_62,_63 for the array count computation
                                    ParameterizedType target = inMap.copyWithArrays(inMap.arrays - tp.arrays);
                                    result.merge(tp.typeParameter, target, ParameterizedType::bestDefined);
                                });
                    }
                    j++;
                }
                if (parameterInfo.parameterizedType.isTypeParameter() && !containsErasedExpressions) {
                    // see MethodCall_48; MethodCall_3 shows why we omit ErasureExpression
                    // see MethodCall_60,_61,_62,_63 for the array count computation
                    boolean oneFewer = expression.returnType().arrays == parameterInfo.parameterizedType.arrays - 1;
                    int paramArrays = parameterInfo.parameterizedType.arrays - (isVarargs && oneFewer ? 1 : 0);
                    int arrays = expression.returnType().arrays - paramArrays;
                    ParameterizedType target = expression.returnType().copyWithArrays(arrays);
                    result.merge(parameterInfo.parameterizedType.typeParameter, target, ParameterizedType::bestDefined);
                }
                i++;
            }
            return new TypeParameterMap(result);
        }
    }


    private FilterResult filterCandidatesByParameters(Map<MethodTypeParameterMap, Integer> methodCandidates,
                                                      Map<Integer, Expression> evaluatedExpressions,
                                                      TypeParameterMap typeParameterMap,
                                                      boolean explain) {
        Map<Integer, Set<ParameterizedType>> acceptedErasedTypes =
                evaluatedExpressions.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e ->
                        e.getValue().erasureTypes(typeContext).stream()
                                .map(pt -> pt.applyTranslation(typeContext.getPrimitives(), typeParameterMap.map()))
                                .collect(Collectors.toUnmodifiableSet())));

        Map<Integer, ParameterizedType> acceptedErasedTypesCombination = null;
        Map<MethodInfo, Integer> compatibilityScore = new HashMap<>();

        for (Map.Entry<MethodTypeParameterMap, Integer> entry : methodCandidates.entrySet()) {
            int sumScore = 0;
            boolean foundCombination = true;
            List<ParameterInfo> parameters = entry.getKey().methodInspection.getParameters();
            int pos = 0;
            Map<Integer, ParameterizedType> thisAcceptedErasedTypesCombination = new TreeMap<>();
            for (ParameterInfo parameterInfo : parameters) {

                Set<ParameterizedType> acceptedErased = acceptedErasedTypes.get(pos);
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
                        for (ParameterizedType actualType : acceptedErased) {

                            int compatible;
                            if (isUnboundMethodTypeParameter(actualType) && actualType.arrays == arrayType.arrays) {
                                compatible = 5;
                            } else {
                                compatible = callIsAssignableFrom(actualType, arrayType, explain);
                            }
                            if (compatible >= 0 && (bestCompatible == Integer.MIN_VALUE || compatible < bestCompatible)) {
                                bestCompatible = compatible;
                                bestAcceptedType = actualType;
                            }
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


                for (ParameterizedType actualType : acceptedErased) {
                    int penaltyForReturnType = computePenaltyForReturnType(actualType, formalType);
                    for (ParameterizedType actualTypeReplaced : actualType.replaceByTypeBounds()) {
                        for (ParameterizedType formalTypeReplaced : formalType.replaceByTypeBounds()) {

                            boolean paramIsErasure = evaluatedExpressions.get(pos) instanceof ErasureExpression;
                            int compatible;
                            if (actualTypeReplaced == ParameterizedType.NULL_CONSTANT) {
                                // compute the distance to Object, so that the nearest one loses. See MethodCall_66
                                // IMPROVE why 100?
                                compatible = 100 - callIsAssignableFrom(formalTypeReplaced, typeContext.getPrimitives().objectParameterizedType(), explain);
                            } else if (paramIsErasure && actualTypeReplaced != actualType) {
                                /*
                                 See 'method' call in MethodCall_32; this feels like a hack.
                                 Map.get(e.getKey()) call in MethodCall_37 shows the opposite direction; so we do Max.
                                 Feels even more like a hack.
                                 */
                                compatible = Math.max(callIsAssignableFrom(formalTypeReplaced, actualTypeReplaced, explain),
                                        callIsAssignableFrom(actualTypeReplaced, formalTypeReplaced, explain));
                            } else {
                                compatible = callIsAssignableFrom(actualTypeReplaced, formalTypeReplaced, explain);
                            }

                            if (compatible >= 0 && (bestCompatible == Integer.MIN_VALUE
                                    || (compatible + penaltyForReturnType) < bestCompatible)) {
                                bestCompatible = compatible + penaltyForReturnType;
                                bestAcceptedType = actualType;
                            }
                        }
                    }
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
                int varargsSkipped = Math.abs(evaluatedExpressions.size() - parameters.size());
                int methodDistance = entry.getValue();
                sumScore += 100 * methodDistance + 10 * varargsSkipped;
                if (acceptedErasedTypesCombination == null) {
                    acceptedErasedTypesCombination = thisAcceptedErasedTypesCombination;
                } else if (!acceptedErasedTypesCombination.equals(thisAcceptedErasedTypesCombination)) {
                    LOGGER.debug("Looks like multiple, different, combinations? {} to {}", acceptedErasedTypesCombination,
                            thisAcceptedErasedTypesCombination);
                }
            }
            compatibilityScore.put(entry.getKey().methodInspection.getMethodInfo(), sumScore);
        }

        // remove those with a negative compatibility score
        methodCandidates.entrySet().removeIf(e -> {
            int score = compatibilityScore.get(e.getKey().methodInspection.getMethodInfo());
            return score < 0;
        });

        return new FilterResult(evaluatedExpressions, compatibilityScore);
    }

    private int computePenaltyForReturnType(ParameterizedType actualType,
                                            ParameterizedType formalType) {
        if (actualType.typeInfo == null || formalType.typeInfo == null) return 0;
        TypeInspection actualTypeInspection = typeContext.getTypeInspection(actualType.typeInfo);
        MethodInspection actual = actualTypeInspection.getSingleAbstractMethod();
        if (actual == null) return 0; // not worth the effort
        TypeInspection formalTypeInspection = typeContext.getTypeInspection(formalType.typeInfo);
        MethodInspection formal = formalTypeInspection.getSingleAbstractMethod();
        if (formal == null) return 0;
        if (actual.isVoid() && !formal.isVoid()) return NOT_ASSIGNABLE;
        // we have to have a small penalty in the other direction, to give preference to a Consumer when a Function is competing
        if (!actual.isVoid() && formal.isVoid()) return 5;
        return 0;
    }

    private boolean isUnboundMethodTypeParameter(ParameterizedType actualType) {
        return actualType.typeParameter != null
                && actualType.typeParameter.isMethodTypeParameter()
                && actualType.typeParameter.getTypeBounds().isEmpty();
    }

    private FilterResult filterMethodCandidatesInErasureMode(ExpressionContext expressionContext,
                                                             Map<MethodTypeParameterMap, Integer> methodCandidates,
                                                             List<com.github.javaparser.ast.expr.Expression> expressions) {
        Map<Integer, Expression> evaluatedExpressions = new HashMap<>();
        Map<MethodInfo, Integer> compatibilityScore = new HashMap<>();
        int pos = 0;
        while (!methodCandidates.isEmpty() && evaluatedExpressions.size() < expressions.size()) {
            ForwardReturnTypeInfo forward = new ForwardReturnTypeInfo(null, true);
            Expression evaluatedExpression = expressionContext.parseExpression(expressions.get(pos), forward);
            evaluatedExpressions.put(pos, Objects.requireNonNull(evaluatedExpression));
            filterCandidatesByParameter(evaluatedExpression, pos, methodCandidates, compatibilityScore);
            pos++;
        }
        return new FilterResult(evaluatedExpressions, compatibilityScore);
    }


    /**
     * StringBuilder.length() is in public interface CharSequence and in the private type AbstractStringBuilder.
     * We prioritise the CharSequence version, because that one can be annotated using annotated APIs.
     *
     * @param methodCandidates the candidates to sort
     * @return a list of size>1 when also candidate 1 is accessible... this will result in an error?
     */
    private List<MethodTypeParameterMap> sortRemainingCandidatesByShallowPublic(Map<MethodTypeParameterMap, Integer> methodCandidates) {
        if (methodCandidates.size() > 1) {
            Comparator<MethodTypeParameterMap> comparator =
                    (m1, m2) -> {
                        boolean m1Accessible = m1.methodInspection.getMethodInfo().analysisAccessible(typeContext);
                        boolean m2Accessible = m2.methodInspection.getMethodInfo().analysisAccessible(typeContext);
                        if (m1Accessible && !m2Accessible) return -1;
                        if (m2Accessible && !m1Accessible) return 1;
                        return 0; // don't know what to prioritize
                    };
            List<MethodTypeParameterMap> sorted = new ArrayList<>(methodCandidates.keySet());
            sorted.sort(comparator);
            MethodTypeParameterMap m1 = sorted.get(1);
            if (m1.methodInspection.getMethodInfo().analysisAccessible(typeContext)) {
                return sorted;
            }
            return List.of(sorted.get(0));
        }
        // not two accessible
        return List.copyOf(methodCandidates.keySet());
    }

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
        Primitives primitives = typeContext.getPrimitives();
        ParameterizedType parameterType = method.getConcreteTypeOfParameter(primitives, i);
        if (outsideContext == null || outsideContext.isVoid() || outsideContext.typeInfo == null) {
            // Cannot do better than parameter type, have no outside context;
            ParameterizedType translated = parameterType.applyTranslation(primitives, extra.map());
            return new ForwardReturnTypeInfo(translated, false, extra);
        }
        Set<TypeParameter> typeParameters = parameterType.extractTypeParameters();
        Map<NamedType, ParameterizedType> outsideMap = outsideContext.initialTypeParameterMap(typeContext);
        if (typeParameters.isEmpty() || outsideMap.isEmpty()) {
            if (outsideMap.isEmpty()) {
                /* here we test whether the return type of the method is a method type parameter. If so,
                   we have and outside type that we can assign to it. See MethodCall_68, assigning B to type parameter T
                 */
                ParameterizedType returnType = method.methodInspection.getReturnType();
                if (returnType.typeParameter != null) {
                    Map<NamedType, ParameterizedType> translate = Map.of(returnType.typeParameter, outsideContext);
                    ParameterizedType translated = parameterType.applyTranslation(primitives, translate);
                    return new ForwardReturnTypeInfo(translated, false, extra);
                }
            }
            // No type parameters to fill in or to extract
            ParameterizedType translated = parameterType.applyTranslation(primitives, extra.map());
            return new ForwardReturnTypeInfo(translated, false, extra);
        }
        Map<NamedType, ParameterizedType> translate = new HashMap<>(extra.map());
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
        ParameterizedType translated = parameterType.applyTranslation(primitives, translate);
        // Translated context and parameter
        return new ForwardReturnTypeInfo(translated, false, extra);

    }


    private TypeParameter tryToFindTypeTypeParameter(MethodTypeParameterMap method,
                                                     TypeParameter methodTypeParameter) {
        ParameterizedType formalReturnType = method.methodInspection.getReturnType();
        Map<NamedType, ParameterizedType> map = formalReturnType.initialTypeParameterMap(typeContext);
        // map points from E as 0 in List to E as 0 in List.of()
        return map.entrySet().stream().filter(e -> methodTypeParameter.equals(e.getValue().typeParameter))
                .map(e -> (TypeParameter) e.getKey()).findFirst().orElse(null);
    }

    private static void trimMethodsWithBestScore(Map<MethodTypeParameterMap, Integer> methodCandidates,
                                                 Map<MethodInfo, Integer> compatibilityScore) {
        int min = methodCandidates.keySet().stream()
                .mapToInt(mc -> compatibilityScore.getOrDefault(mc.methodInspection.getMethodInfo(), 0))
                .min().orElseThrow();
        if (min == NOT_ASSIGNABLE) throw new UnsupportedOperationException();
        methodCandidates.keySet().removeIf(e ->
                compatibilityScore.getOrDefault(e.methodInspection.getMethodInfo(), 0) > min);
    }

    // remove varargs if there's also non-varargs solutions
    //
    // this step if AFTER the score step, so we've already dealt with type conversions.
    // we still have to deal with overloads in supertypes, methods with the same type signature
    private static void trimVarargsVsMethodsWithFewerParameters(Map<MethodTypeParameterMap, Integer> methodCandidates) {
        int countVarargs = (int) methodCandidates.keySet().stream().filter(e -> e.methodInspection.isVarargs()).count();
        if (countVarargs > 0 && countVarargs < methodCandidates.size()) {
            methodCandidates.keySet().removeIf(e -> e.methodInspection.isVarargs());
        }
    }

    private void filterCandidatesByParameter(Expression expression,
                                             int pos,
                                             Map<MethodTypeParameterMap, Integer> methodCandidates,
                                             Map<MethodInfo, Integer> compatibilityScore) {
        methodCandidates.keySet().removeIf(mc -> {
            int score = compatibleParameter(expression, pos, mc.methodInspection);
            if (score >= 0) {
                Integer inMap = compatibilityScore.get(mc.methodInspection.getMethodInfo());
                inMap = inMap == null ? score : score + inMap;
                compatibilityScore.put(mc.methodInspection.getMethodInfo(), inMap);
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
            Set<ParameterizedType> types = evaluatedExpression.erasureTypes(typeContext);
            return types.stream().mapToInt(type -> callIsAssignableFrom(type, typeOfParameter, false))
                    .reduce(NOT_ASSIGNABLE, (v0, v1) -> {
                        if (v0 < 0) return v1;
                        if (v1 < 0) return v0;
                        return Math.min(v0, v1);
                    });
        }

        /* If the evaluated expression is a method with type parameters, then these type parameters
         are allowed in a reverse way (expect List<String>, accept List<T> with T a type parameter of the method,
         as long as T <- String).
        */
        return callIsAssignableFrom(evaluatedExpression.returnType(), typeOfParameter, false);
    }

    private int callIsAssignableFrom(ParameterizedType actualType, ParameterizedType typeOfParameter, boolean explain) {
        int value = new IsAssignableFrom(typeContext, typeOfParameter, actualType).execute(false, COVARIANT_ERASURE);
        if (explain) {
            LOGGER.error("{} <- {} = {}", typeOfParameter, actualType, value);
        }
        return value;
    }
}
