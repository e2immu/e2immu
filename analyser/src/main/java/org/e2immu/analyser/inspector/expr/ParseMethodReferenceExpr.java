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

import com.github.javaparser.ast.expr.MethodReferenceExpr;
import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.inspector.ForwardReturnTypeInfo;
import org.e2immu.analyser.inspector.MethodTypeParameterMap;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.LambdaExpressionErasures;
import org.e2immu.analyser.model.expression.MethodReference;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ParseMethodReferenceExpr {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseMethodReferenceExpr.class);

    public static Expression parse(ExpressionContext expressionContext,
                                   MethodReferenceExpr methodReferenceExpr,
                                   ForwardReturnTypeInfo forwardReturnTypeInfo) {
        TypeContext typeContext = expressionContext.typeContext();
        MethodTypeParameterMap singleAbstractMethod = forwardReturnTypeInfo.computeSAM(typeContext,
                expressionContext.primaryType());
        assert singleAbstractMethod != null && singleAbstractMethod.isSingleAbstractMethod();

        LOGGER.debug("Start parsing method reference {}", methodReferenceExpr);

        Expression scope = expressionContext.parseExpressionStartVoid(methodReferenceExpr.getScope());
        boolean scopeIsAType = scopeIsAType(scope);
        Scope.ScopeNature scopeNature = scopeIsAType ? Scope.ScopeNature.STATIC :
                Scope.ScopeNature.INSTANCE;
        ParameterizedType parameterizedType = scope.returnType();
        String methodName = methodReferenceExpr.getIdentifier();
        boolean constructor = "new".equals(methodName);

        int parametersPresented = singleAbstractMethod.methodInspection.getParameters().size();
        Map<MethodTypeParameterMap, Integer> methodCandidates;
        String methodNameForErrorReporting;
        if (constructor) {
            if (parameterizedType.arrays > 0) {
                return arrayConstruction(typeContext, Identifier.from(methodReferenceExpr), parameterizedType);
            }
            methodNameForErrorReporting = "constructor";
            methodCandidates = typeContext.resolveConstructor(parameterizedType, parameterizedType,
                    parametersPresented, parameterizedType.initialTypeParameterMap(typeContext));
        } else {
            methodCandidates = new HashMap<>();
            methodNameForErrorReporting = "method " + methodName;

            // the following examples say that you should look for a method with identical number of parameters:
            // e.g. Function<T, R> has R apply(T t), and we present C::new (where C has a constructor C(String s) { .. }
            // e.g. Consumer<T> has void accept(T t), and we present System.out::println
            // e.g. Functional interface LogMethod:  void log(LogTarget logTarget, String message, Object... objects), we present Logger::logViaLogBackClassic

            // but this example says you need to subtract:
            // e.g. Function<T, R> has R apply(T t), and we present Object::toString (the scope is the first argument)
            typeContext.recursivelyResolveOverloadedMethods(parameterizedType,
                    methodName, parametersPresented, scopeIsAType, parameterizedType.initialTypeParameterMap(typeContext),
                    methodCandidates, scopeNature);
        }
        if (methodCandidates.isEmpty()) {
            throw new UnsupportedOperationException("Cannot find a candidate for " + methodNameForErrorReporting
                    + " at " + methodReferenceExpr.getBegin());
        }
        List<MethodTypeParameterMap> sorted;
        if (methodCandidates.size() > 1) {
            sorted = handleMultipleCandidates(typeContext, expressionContext, singleAbstractMethod, methodCandidates,
                    scopeIsAType, constructor);
        } else {
            sorted = List.copyOf(methodCandidates.keySet());
        }
        if (sorted.isEmpty()) {
            throw new UnsupportedOperationException("I've killed all the candidates myself??");
        }
        MethodTypeParameterMap method = sorted.get(0);
        List<ParameterizedType> types = inputTypes(typeContext.getPrimitives(), parameterizedType, method, parametersPresented);
        ParameterizedType concreteReturnType = method.getConcreteReturnType(typeContext.getPrimitives());
        ParameterizedType functionalType = singleAbstractMethod.inferFunctionalType(typeContext, types,
                concreteReturnType);
        LOGGER.debug("End parsing method reference {}, found {}", methodNameForErrorReporting,
                method.methodInspection.getDistinguishingName());
        return new MethodReference(Identifier.from(methodReferenceExpr),
                scope, method.methodInspection.getMethodInfo(), functionalType);
    }

    private static List<MethodTypeParameterMap> handleMultipleCandidates(TypeContext typeContext,
                                                                         ExpressionContext expressionContext,
                                                                         MethodTypeParameterMap singleAbstractMethod,
                                                                         Map<MethodTypeParameterMap, Integer> methodCandidates,
                                                                         boolean scopeIsAType,
                                                                         boolean constructor) {
        List<MethodTypeParameterMap> sorted = new ArrayList<>(methodCandidates.keySet());
        // check types of parameters in SAM
        // see if the method candidate's type fits the SAMs
        for (int i = 0; i < singleAbstractMethod.methodInspection.getParameters().size(); i++) {
            final int index = i;
            ParameterizedType concreteType = singleAbstractMethod.getConcreteTypeOfParameter(typeContext.getPrimitives(), i);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Have {} candidates, try to weed out based on compatibility of {} with parameter {}",
                        sorted.size(), concreteType.detailedString(expressionContext.typeContext()), i);
            }
            List<MethodTypeParameterMap> copy = new ArrayList<>(sorted);
            copy.removeIf(mt -> {
                ParameterizedType typeOfMethodCandidate = typeOfMethodCandidate(typeContext, mt, index, scopeIsAType, constructor);
                boolean isAssignable = typeOfMethodCandidate.isAssignableFrom(typeContext, concreteType);
                return !isAssignable;
            });
            // only accept of this is an improvement
            // there are situations where this method kills all, as the concrete type
            // can be a type parameter while the method candidates only have concrete types
            if (!copy.isEmpty() && copy.size() < sorted.size()) {
                sorted.retainAll(copy);
            }
            // sort on assignability to parameter "index"
            sorted.sort((mc1, mc2) -> {
                ParameterizedType typeOfMc1 = typeOfMethodCandidate(typeContext, mc1, index, scopeIsAType, constructor);
                ParameterizedType typeOfMc2 = typeOfMethodCandidate(typeContext, mc2, index, scopeIsAType, constructor);
                if (typeOfMc1.equals(typeOfMc2)) return 0;
                return typeOfMc2.isAssignableFrom(typeContext, typeOfMc1) ? -1 : 1;
            });
        }
        if (sorted.size() > 1) {
            LOGGER.debug("Trying to weed out those of the same type, static vs instance");
            staticVsInstance(sorted);
            if (sorted.size() > 1) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Still have {}", methodCandidates.size());
                    sorted.forEach(mc -> LOGGER.debug("- {}", mc.methodInspection));
                }
                // method candidates have been sorted; the first one should be the one we're after and others should be
                // higher in the hierarchy (interfaces, parent classes)
            }
        }
        return sorted;
    }

    /**
     * In this method we provide the types that the "inferFunctionalType" method will use to determine
     * the functional type. We must provide a concrete type for each of the real method's parameters.
     */
    private static List<ParameterizedType> inputTypes(Primitives primitives,
                                                      ParameterizedType parameterizedType,
                                                      MethodTypeParameterMap method,
                                                      int parametersPresented) {
        List<ParameterizedType> types = new ArrayList<>();
        if (method.methodInspection.getParameters().size() < parametersPresented) {
            types.add(parameterizedType);
        }
        method.methodInspection.getParameters().stream().map(p -> p.parameterizedType)
                .forEach(pt -> {
                    ParameterizedType translated = pt.applyTranslation(primitives, method.concreteTypes);
                    types.add(translated);
                });
        return types;
    }

    private static ParameterizedType typeOfMethodCandidate(InspectionProvider inspectionProvider,
                                                           MethodTypeParameterMap mt,
                                                           int index,
                                                           boolean scopeIsAType,
                                                           boolean constructor) {
        MethodInfo methodInfo = mt.methodInspection.getMethodInfo();
        int param = scopeIsAType && !constructor && !mt.methodInspection.isStatic() ? index - 1 : index;
        if (param == -1) {
            return methodInfo.typeInfo.asParameterizedType(inspectionProvider);
        }
        List<ParameterInfo> parameters = mt.methodInspection.getParameters();
        return parameters.get(param).parameterizedType;
    }

    private static void staticVsInstance(List<MethodTypeParameterMap> methodCandidates) {
        Set<TypeInfo> haveInstance = new HashSet<>();

        methodCandidates.stream()
                .filter(mt -> !mt.methodInspection.isStatic())
                .forEach(mt -> haveInstance.add(mt.methodInspection.getMethodInfo().typeInfo));
        methodCandidates.removeIf(mt -> mt.methodInspection.isStatic() &&
                haveInstance.contains(mt.methodInspection.getMethodInfo().typeInfo));
    }

    public static Expression erasure(ExpressionContext expressionContext, MethodReferenceExpr methodReferenceExpr) {
        Expression scope = expressionContext.parseExpressionStartVoid(methodReferenceExpr.getScope());
        ParameterizedType parameterizedType = scope.returnType();
        String methodName = methodReferenceExpr.getIdentifier();
        boolean constructor = "new".equals(methodName);

        TypeContext typeContext = expressionContext.typeContext();
        Map<MethodTypeParameterMap, Integer> methodCandidates;
        if (constructor) {
            if (parameterizedType.arrays > 0) {
                return arrayConstruction(typeContext, Identifier.from(methodReferenceExpr), parameterizedType);
            }
            methodCandidates = typeContext.resolveConstructor(parameterizedType, parameterizedType,
                    TypeContext.IGNORE_PARAMETER_NUMBERS, parameterizedType.initialTypeParameterMap(typeContext));
        } else {
            methodCandidates = new HashMap<>();
            typeContext.recursivelyResolveOverloadedMethods(parameterizedType,
                    methodName, TypeContext.IGNORE_PARAMETER_NUMBERS, false,
                    parameterizedType.initialTypeParameterMap(typeContext), methodCandidates,
                    Scope.ScopeNature.INSTANCE);
        }
        if (methodCandidates.isEmpty()) {
            throw new UnsupportedOperationException("Cannot find a candidate for " +
                    (constructor ? "constructor" : methodName) + " at " + methodReferenceExpr.getBegin());
        }
        Set<LambdaExpressionErasures.Count> erasures = new HashSet<>();
        for (MethodTypeParameterMap mt : methodCandidates.keySet()) {
            LOGGER.debug("Found method reference candidate, this can work: {}", mt.methodInspection);
            MethodInspection methodInspection = mt.methodInspection;
            boolean scopeIsType = scopeIsAType(scope);
            boolean addOne = scopeIsType && !methodInspection.getMethodInfo().isConstructor && !methodInspection.isStatic();
            int n = methodInspection.getParameters().size() + (addOne ? 1 : 0);
            boolean isVoid = !constructor && methodInspection.isVoid();
            erasures.add(new LambdaExpressionErasures.Count(n, isVoid));
        }
        LOGGER.debug("End parsing unevaluated method reference {}, found counts {}", methodReferenceExpr, erasures);
        return new LambdaExpressionErasures(erasures, expressionContext.getLocation());
    }

    private static MethodReference arrayConstruction(TypeContext typeContext,
                                                     Identifier identifier,
                                                     ParameterizedType parameterizedType) {
        MethodInfo arrayConstructor = ParseArrayCreationExpr.createArrayCreationConstructor(typeContext, parameterizedType);
        TypeInfo intFunction = typeContext.typeMap.get("java.util.function.IntFunction");
        if (intFunction == null) throw new UnsupportedOperationException("? need IntFunction");
        ParameterizedType intFunctionPt = new ParameterizedType(intFunction, List.of(parameterizedType));
        return new MethodReference(Identifier.generate("method reference in array construction"),
                new TypeExpression(identifier, parameterizedType, Diamond.NO), arrayConstructor, intFunctionPt);
    }

    public static boolean scopeIsAType(Expression scope) {
        return scope instanceof TypeExpression;
    }


}
