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
import org.e2immu.analyser.inspector.TypeParameterMap;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.Map;


/**
 * @param expression The scope expression
 * @param type       The concrete type of the scope expression, or the formal type of the context of the method call
 *                   It cannot be the method's return type, because we don't know the method yet.
 * @param nature     The scope nature: ABSENT when no expression, STATIC when the scope is a TypeExpression
 */
public record Scope(Expression expression,
                    ParameterizedType type,
                    ScopeNature nature,
                    TypeParameterMap typeParameterMap) {

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
                                     Identifier identifier,
                                     InspectionProvider inspectionProvider,
                                     boolean scopeIsThis,
                                     ExpressionContext expressionContext) {
        /*
         in 3 situations, we compute (or potentially correct) the scope.
         In the case of a static method, we always replace by the class containing the method.
         In the case of this: we use the type of the current class in case of extension, but not in case of sub-typing,
         because we have to be able to indicate that we're reading the correct "this" in the VariableAccess report.
         In case of parent-child, we activate the "super" boolean.
         See e.g. Lambda_15.
         IMPROVE! https://github.com/e2immu/e2immu/issues/60
         */
        MethodInfo methodInfo = methodInspection.getMethodInfo();
        if (objectIsImplicit() || methodInfo.isStatic() || scopeIsThis) {
            TypeInfo exact = methodInfo.typeInfo;
            if (methodInfo.isStatic()) {
                return new TypeExpression(identifier, exact.asParameterizedType(inspectionProvider), Diamond.NO);
            }
            TypeInfo current = expressionContext.enclosingType();
            TypeInfo typeInfo;
            boolean writeSuper;
            TypeInfo explicitlyWriteType;
            if (current == exact
                    || exact.isJavaLangObject()
                    || current.recursivelyImplements(inspectionProvider, exact.fullyQualifiedName) != null) {
                typeInfo = current; // the same type
                writeSuper = false;
                explicitlyWriteType = null;
            } else if (current.parentalHierarchyContains(exact, inspectionProvider)) {
                typeInfo = current;
                writeSuper = true;
                explicitlyWriteType = null;
            } else {
                // relationship must be inner class of ...
                typeInfo = exact;
                writeSuper = false;
                explicitlyWriteType = exact;
            }
            Variable thisVariable = new This(inspectionProvider, typeInfo, explicitlyWriteType, writeSuper);
            return new VariableExpression(identifier, thisVariable);
        }
        return expression;
    }

    static Scope computeScope(ExpressionContext expressionContext,
                              InspectionProvider inspectionProvider,
                              MethodCallExpr methodCallExpr,
                              TypeParameterMap extra) {
        ForwardReturnTypeInfo forward = new ForwardReturnTypeInfo(null, false, extra);
        Expression scope = methodCallExpr.getScope().map(e -> expressionContext.parseExpression(e, forward)).orElse(null);
        // depending on the object, we'll need to find the method somewhere
        ParameterizedType scopeType;
        Scope.ScopeNature scopeNature;

        if (scope == null) {
            scopeType = new ParameterizedType(expressionContext.enclosingType(), 0);
            scopeNature = Scope.ScopeNature.ABSENT; // could be static, could be object instance
        } else {
            scopeType = scope.returnType();
            scopeNature = scope instanceof TypeExpression ? Scope.ScopeNature.STATIC : Scope.ScopeNature.INSTANCE;
        }
        Map<NamedType, ParameterizedType> scopeTypeMap = scopeType.initialTypeParameterMap(inspectionProvider);
        return new Scope(scope, scopeType, scopeNature, new TypeParameterMap(scopeTypeMap));
    }

}
