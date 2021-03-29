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
import org.e2immu.analyser.model.expression.UnevaluatedMethodCall;
import org.e2immu.analyser.model.expression.UnevaluatedObjectCreation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParseObjectCreationExpr {
    public static Expression parse(ExpressionContext expressionContext,
                                   ObjectCreationExpr objectCreationExpr,
                                   ParameterizedType impliedParameterizedType) {
        TypeContext typeContext = expressionContext.typeContext;

        Diamond diamond = objectCreationExpr.getType().getTypeArguments()
                .map(list -> list.isEmpty() ? Diamond.YES : Diamond.SHOW_ALL).orElse(Diamond.NO);

        ParameterizedType parameterizedType;
        if (diamond == Diamond.YES) {
            ParameterizedType diamondType = ParameterizedTypeFactory.from(typeContext, objectCreationExpr.getType());
            ParameterizedType formalType = diamondType.typeInfo.asParameterizedType(expressionContext.typeContext);
            if (impliedParameterizedType == null) {
                // we cannot infer (this can happen, when we're choosing a method candidate among many candidates)
                // e.g. map.put(key, new LinkedList<>()) -> we first need to know which "put" method to choose
                // then there'll be a re-evaluation with an implied parameter of "V"
                return new UnevaluatedObjectCreation(formalType);
            }
            parameterizedType = formalType.inferDiamondNewObjectCreation(expressionContext.typeContext, impliedParameterizedType);
        } else {
            parameterizedType = ParameterizedTypeFactory.from(typeContext, objectCreationExpr.getType());
        }

        if (objectCreationExpr.getAnonymousClassBody().isPresent()) {
            TypeInfo anonymousType = new TypeInfo(expressionContext.enclosingType,
                    expressionContext.anonymousTypeCounters.newIndex(expressionContext.primaryType));
            typeContext.typeMapBuilder.ensureTypeAndInspection(anonymousType, TypeInspectionImpl.InspectionState.STARTING_JAVA_PARSER);
            TypeInspector typeInspector = new TypeInspector(typeContext.typeMapBuilder, anonymousType, true);
            typeInspector.inspectAnonymousType(parameterizedType, expressionContext.newVariableContext("anonymous class body"),
                    objectCreationExpr.getAnonymousClassBody().get());
            expressionContext.addNewlyCreatedType(anonymousType);
            return NewObject.withAnonymousClass(anonymousType.fullyQualifiedName,
                    typeContext.getPrimitives(), parameterizedType, anonymousType, diamond);
        }

        Map<NamedType, ParameterizedType> typeMap = parameterizedType.initialTypeParameterMap(typeContext);
        List<TypeContext.MethodCandidate> methodCandidates = typeContext.resolveConstructor(parameterizedType,
                objectCreationExpr.getArguments().size(), typeMap);
        List<Expression> newParameterExpressions = new ArrayList<>();

        MethodTypeParameterMap singleAbstractMethod = impliedParameterizedType == null ? null :
                impliedParameterizedType.findSingleAbstractMethodOfInterface(expressionContext.typeContext);
        MethodTypeParameterMap method = new ParseMethodCallExpr(typeContext)
                .chooseCandidateAndEvaluateCall(expressionContext, methodCandidates, objectCreationExpr.getArguments(),
                        newParameterExpressions, singleAbstractMethod, new HashMap<>(), "constructor",
                        parameterizedType, objectCreationExpr.getBegin().orElseThrow());
        if (method == null) return new UnevaluatedMethodCall(parameterizedType.detailedString() + "::new");
        return NewObject.objectCreation("unevaluated new object", typeContext.getPrimitives(),
                method.methodInspection.getMethodInfo(), parameterizedType, diamond, newParameterExpressions);
    }
}
