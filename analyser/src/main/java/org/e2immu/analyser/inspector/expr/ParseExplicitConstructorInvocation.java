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
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.type.Type;
import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.inspector.TypeParameterMap;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.ExplicitConstructorInvocation;

import java.util.List;

import static org.e2immu.analyser.util.Logger.LogTarget.METHOD_CALL;
import static org.e2immu.analyser.util.Logger.log;

public class ParseExplicitConstructorInvocation {

    public static Statement parse(ExpressionContext expressionContext,
                                  TypeInfo enclosingType,
                                  ExplicitConstructorInvocationStmt statement,
                                  Identifier identifier) {
        Result result = findConstructor(expressionContext,
                enclosingType,
                statement.getArguments(),
                statement.isThis(),
                statement.getTypeArguments().orElse(null),
                statement.getBegin().orElseThrow());
        return new ExplicitConstructorInvocation(identifier, !statement.isThis(),
                result.constructor, result.expressions);
    }

    /*
     Strategy: collect candidates, using isThis to filter out those of the current type
               select the best one and evaluate the parameters in the same way
     */
    private static Result findConstructor(ExpressionContext expressionContext,
                                          TypeInfo enclosingType,
                                          NodeList<com.github.javaparser.ast.expr.Expression> arguments,
                                          boolean isThis,
                                          NodeList<Type> typeParameters,
                                          Position position) {
        TypeContext typeContext = expressionContext.typeContext;

        TypeInfo startingPoint = isThis ? enclosingType
                : typeContext.getTypeInspection(enclosingType).parentClass().typeInfo;

        List<TypeContext.MethodCandidate> methodCandidates = typeContext.resolveConstructorInvocation(startingPoint,
                arguments.size(), typeParameters == null ? 0 : typeParameters.size());

        ParseMethodCallExpr.ErrorInfo errorInfo = new ParseMethodCallExpr.ErrorInfo(isThis ? "this()" : "super()",
                enclosingType.asParameterizedType(typeContext), position);

        TypeParameterMap extra = TypeParameterMap.EMPTY; // TODO

        ParseMethodCallExpr parser = new ParseMethodCallExpr(typeContext);
        ParseMethodCallExpr.Candidate candidate = parser.chooseCandidateAndEvaluateCall(expressionContext,
                methodCandidates,
                arguments,
                typeContext.getPrimitives().voidParameterizedType,
                extra,
                errorInfo);

        assert candidate != null : "Should have found a unique candidate for " + errorInfo;
        log(METHOD_CALL, "Resulting constructor is {}", candidate.method().methodInspection.getMethodInfo().fullyQualifiedName);

        return new Result(candidate.method().methodInspection.getMethodInfo(), candidate.newParameterExpressions());
    }

    private record Result(MethodInfo constructor, List<Expression> expressions) {
    }
}

