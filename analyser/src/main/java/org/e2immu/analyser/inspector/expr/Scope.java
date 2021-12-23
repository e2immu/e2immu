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

import com.github.javaparser.ast.expr.MethodCallExpr;
import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.inspector.ForwardReturnTypeInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.Map;

import static org.e2immu.analyser.util.Logger.LogTarget.METHOD_CALL;
import static org.e2immu.analyser.util.Logger.log;

/**
 * @param expression The scope expression
 * @param type       The concrete type of the scope expression, or the formal type of the context of the method call
 *                   It cannot be the method's return type, because we don't know the method yet.
 * @param nature     The scope nature: ABSENT when no expression, STATIC when the scope is a TypeExpression
 */
public record Scope(Expression expression,
                    ParameterizedType type,
                    ScopeNature nature,
                    Map<NamedType, ParameterizedType> typeMap) {

    public enum ScopeNature {
        ABSENT,
        STATIC,
        INSTANCE,
    }

    public boolean objectIsImplicit() {
        assert expression == null || nature != ScopeNature.ABSENT;
        return expression == null;
    }

    public Expression ensureExplicit(MethodInspection methodInspection,
                                     InspectionProvider inspectionProvider,
                                     ExpressionContext expressionContext) {
        if (objectIsImplicit()) {
            if (methodInspection.isStatic()) {
                return new TypeExpression(methodInspection.getMethodInfo()
                        .typeInfo.asParameterizedType(inspectionProvider), Diamond.NO);
            }
            Variable thisVariable = new This(inspectionProvider, expressionContext.enclosingType);
            return new VariableExpression(thisVariable);
        }
        return expression;
    }

    static Scope computeScope(ExpressionContext expressionContext,
                              ForwardReturnTypeInfo forwardReturnTypeInfo,
                              InspectionProvider inspectionProvider,
                              MethodCallExpr methodCallExpr) {
        Expression scope = methodCallExpr.getScope().map(expressionContext::parseExpression).orElse(null);
        // depending on the object, we'll need to find the method somewhere
        ParameterizedType scopeType;
        Scope.ScopeNature scopeNature;

        if (scope == null) {
            scopeType = new ParameterizedType(expressionContext.enclosingType, 0);
            scopeNature = Scope.ScopeNature.ABSENT; // could be static, could be object instance
        } else {
            scopeType = scope.returnType();
            scopeNature = scope instanceof TypeExpression ? Scope.ScopeNature.STATIC : Scope.ScopeNature.INSTANCE;
        }
        Map<NamedType, ParameterizedType> scopeTypeMap = scopeType.initialTypeParameterMap(inspectionProvider);
        log(METHOD_CALL, "Type map of method call {} is {}", methodCallExpr.getNameAsString(), scopeTypeMap);
        return new Scope(scope, scopeType, scopeNature, scopeTypeMap);
    }

}
