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
import com.github.javaparser.ast.expr.FieldAccessExpr;
import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ArrayLength;
import org.e2immu.analyser.model.expression.FieldAccess;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.resolver.Resolver;

import java.util.List;
import java.util.Optional;

import static org.e2immu.analyser.util.Logger.LogTarget.RESOLVE;
import static org.e2immu.analyser.util.Logger.log;

public class ParseFieldAccessExpr {
    public static Expression parse(ExpressionContext expressionContext, FieldAccessExpr fieldAccessExpr) {
        Expression object = expressionContext.parseExpression(fieldAccessExpr.getScope());
        String name = fieldAccessExpr.getName().asString();

        if (object instanceof PackagePrefixExpression) {
            PackagePrefix packagePrefix = ((PackagePrefixExpression) object).packagePrefix();
            PackagePrefix combined = packagePrefix.append(name);
            if (expressionContext.typeContext.isPackagePrefix(combined)) {
                return new PackagePrefixExpression(combined);
            }
            String fullyQualifiedName = String.join(".", packagePrefix.prefix) + "." + name;
            TypeInfo typeInfo = expressionContext.typeContext.getFullyQualified(fullyQualifiedName, true);
            ParameterizedType objectType = new ParameterizedType(typeInfo, 0);
            return new TypeExpression(objectType, Diamond.NO);
        }
        return createFieldAccess(expressionContext, object, name, fieldAccessExpr.getBegin().orElseThrow());
    }

    public static Expression createFieldAccess(ExpressionContext expressionContext, Expression object, String name, Position positionForErrorReporting) {
        ParameterizedType objectType = object.returnType();
        if (objectType.arrays > 0 && "length".equals(name)) {
            return new ArrayLength(expressionContext.typeContext.getPrimitives(), object);
        }
        if (objectType.typeInfo != null) {
            Optional<FieldInfo> oFieldInfo = Resolver.accessibleFieldsStream(expressionContext.typeContext,
                    objectType.typeInfo, objectType.typeInfo.primaryType())
                    .filter(f -> name.equals(f.name)).findFirst();
            if (oFieldInfo.isPresent()) {
                return fieldAccess(expressionContext, oFieldInfo.get(), object);
            }
            TypeInspection objectTypeInspection = expressionContext.typeContext.getTypeInspection(objectType.typeInfo);
            Optional<TypeInfo> oSubType = objectTypeInspection.subTypes().stream().filter(s -> name.equals(s.name())).findFirst();
            if (oSubType.isPresent()) {
                return new TypeExpression(oSubType.get().asParameterizedType(expressionContext.typeContext), Diamond.NO);
            }
            throw new UnsupportedOperationException("Unknown field or subtype " + name + " in type " + objectType.typeInfo.fullyQualifiedName + " at " + positionForErrorReporting);
        } else {
            throw new UnsupportedOperationException("Object type has no typeInfo? at " + positionForErrorReporting);
        }
    }

    private static FieldAccess fieldAccess(ExpressionContext expressionContext, FieldInfo fieldInfo, Expression object) {
        if (fieldInfo.owner == expressionContext.enclosingType) {
            log(RESOLVE, "Adding dependency on field {}", fieldInfo.fullyQualifiedName());
        }
        // in a static context, the object is a type expression.
        // it can be a method call, such as findNode().data (data is the field)
        // Otherwise, it has to be a variable
        List<Variable> vars = object.variables();
        Variable objectVariable = vars.isEmpty() ? null : vars.get(0);
        FieldReference fieldReference = new FieldReference(expressionContext.typeContext, fieldInfo, objectVariable);
        return new FieldAccess(object, fieldReference);
    }
}
