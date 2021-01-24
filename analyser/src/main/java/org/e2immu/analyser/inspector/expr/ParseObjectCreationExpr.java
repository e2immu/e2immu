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

import com.github.javaparser.ast.expr.ObjectCreationExpr;
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.UnevaluatedMethodCall;
import org.e2immu.analyser.model.expression.UnevaluatedObjectCreation;
import org.e2immu.analyser.objectflow.ObjectFlow;

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
            if(impliedParameterizedType == null) {
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
            return NewObject.withAnonymousClass(typeContext.getPrimitives(), parameterizedType, anonymousType, diamond);
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
        return NewObject.objectCreation(typeContext.getPrimitives(),
                method.methodInspection.getMethodInfo(), parameterizedType, diamond, newParameterExpressions, ObjectFlow.NO_FLOW);
    }
}
