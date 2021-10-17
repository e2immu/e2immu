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

import com.github.javaparser.ast.expr.ObjectCreationExpr;
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.UnevaluatedObjectCreation;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.*;

public class ParseObjectCreationExpr {
    public static Expression parse(ExpressionContext expressionContext,
                                   ObjectCreationExpr objectCreationExpr,
                                   ForwardReturnTypeInfo forwardReturnTypeInfo) {
        TypeContext typeContext = expressionContext.typeContext;

        ParameterizedType typeAsIs = ParameterizedTypeFactory.from(typeContext, objectCreationExpr.getType());
        ParameterizedType formalType = typeAsIs.typeInfo.asParameterizedType(expressionContext.typeContext);

        Diamond diamond = objectCreationExpr.getType().getTypeArguments()
                .map(list -> list.isEmpty() ? Diamond.YES : Diamond.SHOW_ALL).orElse(Diamond.NO);

        ParameterizedType impliedParameterizedType = forwardReturnTypeInfo.type();
        ParameterizedType parameterizedType;
        if (diamond == Diamond.YES) {
            if (impliedParameterizedType != null) {
                parameterizedType = formalType.inferDiamondNewObjectCreation(expressionContext.typeContext, impliedParameterizedType);
            } else {
                // we cannot infer (this can happen, when we're choosing a method candidate among many candidates)
                // e.g. map.put(key, new LinkedList<>()) -> we first need to know which "put" method to choose
                // then there'll be a re-evaluation with an implied parameter of "V"
                parameterizedType = null;
            }
        } else {
            parameterizedType = typeAsIs;
        }

        if (objectCreationExpr.getAnonymousClassBody().isPresent()) {
            if (parameterizedType == null) {
                return new UnevaluatedObjectCreation(formalType);
            }
            TypeInfo anonymousType = new TypeInfo(expressionContext.enclosingType,
                    expressionContext.anonymousTypeCounters.newIndex(expressionContext.primaryType));
            typeContext.typeMapBuilder.add(anonymousType, TypeInspectionImpl.InspectionState.STARTING_JAVA_PARSER);
            TypeInspector typeInspector = new TypeInspector(typeContext.typeMapBuilder, anonymousType, true);
            typeInspector.inspectAnonymousType(parameterizedType, expressionContext.newVariableContext("anonymous class body"),
                    objectCreationExpr.getAnonymousClassBody().get());
            expressionContext.addNewlyCreatedType(anonymousType);
            return NewObject.withAnonymousClass(parameterizedType, anonymousType, diamond);
        }

        Map<NamedType, ParameterizedType> typeMap = parameterizedType == null ? null :
                parameterizedType.initialTypeParameterMap(typeContext);
        List<TypeContext.MethodCandidate> methodCandidates = typeContext.resolveConstructor(formalType, parameterizedType,
                objectCreationExpr.getArguments().size(), parameterizedType == null ? Map.of() : typeMap);
        List<Expression> newParameterExpressions = new ArrayList<>();

        MethodTypeParameterMap singleAbstractMethod = impliedParameterizedType == null ? null :
                impliedParameterizedType.findSingleAbstractMethodOfInterface(expressionContext.typeContext);
        MethodTypeParameterMap method = new ParseMethodCallExpr(typeContext)
                .chooseCandidateAndEvaluateCall(expressionContext, methodCandidates, objectCreationExpr.getArguments(),
                        newParameterExpressions, singleAbstractMethod, new HashMap<>(), "constructor",
                        parameterizedType == null ? formalType : parameterizedType, objectCreationExpr.getBegin().orElseThrow());
        if (method == null) {
            if (parameterizedType == null) {
                return new UnevaluatedObjectCreation(formalType);
            }
            return new UnevaluatedObjectCreation(parameterizedType);
        }
        ParameterizedType finalParameterizedType;
        if (parameterizedType == null) {
            // there's only one method left, so we can derive the parameterized type from the parameters
            Set<ParameterizedType> typeParametersResolved = new HashSet<>(formalType.parameters);
            finalParameterizedType = tryToResolveTypeParameters(expressionContext.typeContext,
                    formalType, method, typeParametersResolved, newParameterExpressions);
            if (finalParameterizedType == null) {
                return new UnevaluatedObjectCreation(formalType);
            }
        } else {
            finalParameterizedType = parameterizedType;
        }
        // IMPORTANT: every newly created object is different from each other, UNLESS we're a record, then
        // we can check the constructors... See EqualityMode
        return NewObject.objectCreation(Identifier.generate(), method.methodInspection.getMethodInfo(),
                finalParameterizedType, diamond, newParameterExpressions);
    }

    private static ParameterizedType tryToResolveTypeParameters(InspectionProvider inspectionProvider,
                                                                ParameterizedType formalType,
                                                                MethodTypeParameterMap method,
                                                                Set<ParameterizedType> typeParametersResolved,
                                                                List<Expression> newParameterExpressions) {
        int i = 0;
        Map<NamedType, ParameterizedType> map = new HashMap<>();
        for (Expression parameterExpression : newParameterExpressions) {
            ParameterizedType formalParameterType = method.methodInspection.formalParameterType(i++);
            ParameterizedType concreteArgumentType = parameterExpression.returnType();
            tryToResolveTypeParametersBasedOnOneParameter(inspectionProvider,
                    formalParameterType, concreteArgumentType, map);
            typeParametersResolved.removeIf(pt -> map.containsKey(pt.typeParameter));
            if (typeParametersResolved.isEmpty()) {
                List<ParameterizedType> concreteParameters = formalType.parameters.stream()
                        .map(pt -> map.getOrDefault(pt.typeParameter, pt)).toList();
                return new ParameterizedType(formalType.typeInfo, concreteParameters);
            }
        }
        return null;
    }

    // concreteType Collection<X>, formalType Collection<E>, with E being the parameter in HashSet<E> which implements Collection<E>
    // add E -> X to the map
    // we need the intermediate step to original because the result of translateMap contains E=#0 in Collection

    private static void tryToResolveTypeParametersBasedOnOneParameter(InspectionProvider inspectionProvider,
                                                                      ParameterizedType formalType,
                                                                      ParameterizedType concreteType,
                                                                      Map<NamedType, ParameterizedType> mapAll) {
        if (formalType.typeParameter != null) {
            mapAll.put(formalType.typeParameter, concreteType);
            return;
        }
        if (formalType.typeInfo != null) {
            Map<NamedType, ParameterizedType> map = formalType.translateMap(inspectionProvider, concreteType, true);
            map.forEach((namedType, pt) -> {
                if (namedType instanceof TypeParameter tp) {
                    ParameterizedType original = formalType.parameters.get(tp.getIndex());
                    if (original.typeParameter != null) {
                        mapAll.put(original.typeParameter, pt);
                    }
                }
            });
            return;
        }
        throw new UnsupportedOperationException("?");
    }
}
