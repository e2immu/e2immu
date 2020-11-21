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

import com.github.javaparser.ast.expr.ObjectCreationExpr;
import org.e2immu.analyser.inspector.TypeInspector;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.UnevaluatedMethodCall;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.parser.TypeContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParseObjectCreationExpr {
    public static Expression parse(ExpressionContext expressionContext, ObjectCreationExpr objectCreationExpr, MethodTypeParameterMap singleAbstractMethod) {
        ParameterizedType parameterizedType = ParameterizedType.from(expressionContext.typeContext, objectCreationExpr.getType());

        if (objectCreationExpr.getAnonymousClassBody().isPresent()) {
            // TODO parameterizedType can be Iterator<>, we will need to detect the correct type from context if needed
            TypeInfo anonymousType = new TypeInfo(expressionContext.enclosingType, expressionContext.topLevel.newIndex(expressionContext.enclosingType));
            TypeInspector typeInspector = new TypeInspector(expressionContext.typeContext.typeMapBuilder, anonymousType);
            typeInspector.inspectAnonymousType(parameterizedType, expressionContext.newVariableContext("anonymous class body"),
                    objectCreationExpr.getAnonymousClassBody().get());
            anonymousType.typeInspection.set(typeInspector.build());
            expressionContext.addNewlyCreatedType(anonymousType);
            return new NewObject(parameterizedType, anonymousType);
        }

        Map<NamedType, ParameterizedType> typeMap = parameterizedType.initialTypeParameterMap();
        List<TypeContext.MethodCandidate> methodCandidates = expressionContext.typeContext.resolveConstructor(parameterizedType, objectCreationExpr.getArguments().size(), typeMap);
        List<Expression> newParameterExpressions = new ArrayList<>();
        MethodTypeParameterMap method = ParseMethodCallExpr.chooseCandidateAndEvaluateCall(expressionContext, methodCandidates, objectCreationExpr.getArguments(),
                newParameterExpressions, singleAbstractMethod, new HashMap<>(), "constructor",
                parameterizedType, objectCreationExpr.getBegin().orElseThrow());
        if (method == null) return new UnevaluatedMethodCall(parameterizedType.detailedString() + "::new");
        return new NewObject(method.methodInspection.getMethodInfo(), parameterizedType, newParameterExpressions, null);
    }
}
