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

import com.github.javaparser.ast.expr.FieldAccessExpr;
import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.inspector.ForwardReturnTypeInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ArrayLength;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.impl.FieldReferenceImpl;
import org.e2immu.analyser.resolver.impl.ResolverImpl;

import java.util.List;
import java.util.Optional;

public class ParseFieldAccessExpr {
    public static Expression parse(ExpressionContext expressionContext,
                                   FieldAccessExpr fieldAccessExpr,
                                   ForwardReturnTypeInfo forwardReturnTypeInfo) {
        ForwardReturnTypeInfo forward = new ForwardReturnTypeInfo(null, false,
                forwardReturnTypeInfo.extra());
        Expression object = expressionContext.parseExpression(fieldAccessExpr.getScope(), forward);
        String name = fieldAccessExpr.getName().asString();

        Identifier identifier = Identifier.from(fieldAccessExpr.getBegin().orElseThrow(),
                fieldAccessExpr.getEnd().orElseThrow());

        if (object instanceof PackagePrefixExpression) {
            PackagePrefix packagePrefix = ((PackagePrefixExpression) object).packagePrefix();
            PackagePrefix combined = packagePrefix.append(name);
            if (expressionContext.typeContext().isPackagePrefix(combined)) {
                return new PackagePrefixExpression(combined);
            }
            String fullyQualifiedName = String.join(".", packagePrefix.prefix()) + "." + name;
            TypeInfo typeInfo = expressionContext.typeContext().getFullyQualified(fullyQualifiedName, true);
            ParameterizedType objectType = new ParameterizedType(typeInfo, 0);
            return new TypeExpression(identifier, objectType, Diamond.NO);
        }

        return createFieldAccess(expressionContext, object, name, identifier);
    }

    public static Expression createFieldAccess(ExpressionContext expressionContext,
                                               Expression object,
                                               String name,
                                               Identifier identifier) {
        ParameterizedType objectType = object.returnType();
        if (objectType.arrays > 0 && "length".equals(name)) {
            return new ArrayLength(identifier, expressionContext.typeContext().getPrimitives(), object);
        }
        if (objectType.typeInfo != null) {
            Expression res = findFieldOrSubType(identifier, objectType.typeInfo, object, name, expressionContext);
            if (res == null) {
                throw new UnsupportedOperationException("Unknown field or subtype " + name + " in type "
                        + objectType.typeInfo.fullyQualifiedName + " at " + identifier);
            }
            return res;
        }
        if (objectType.typeParameter != null) {
            List<ParameterizedType> types = objectType.replaceByTypeBounds();
            for (ParameterizedType pt : types) {
                if (pt.typeInfo != null) {
                    Expression res = findFieldOrSubType(identifier, pt.typeInfo, object, name, expressionContext);
                    if (res != null) {
                        return res;
                    }
                }
            }
        }
        throw new UnsupportedOperationException("Object type has no typeInfo? at " + identifier);
    }

    private static Expression findFieldOrSubType(Identifier identifier,
                                                 TypeInfo typeInfo,
                                                 Expression object,
                                                 String name,
                                                 ExpressionContext expressionContext) {
        Optional<FieldInfo> oFieldInfo = ResolverImpl.accessibleFieldsStream(expressionContext.typeContext(), typeInfo,
                        typeInfo.primaryType())
                .filter(f -> name.equals(f.name)).findFirst();
        if (oFieldInfo.isPresent()) {
            TypeInfo enclosingType = expressionContext.enclosingType();
            expressionContext.fieldAccessStore().add(oFieldInfo.get(), enclosingType);
            return new VariableExpression(identifier, new FieldReferenceImpl(expressionContext.typeContext(),
                    oFieldInfo.get(), object, enclosingType));
        }
        TypeInspection objectTypeInspection = expressionContext.typeContext().getTypeInspection(typeInfo);
        Optional<TypeInfo> oSubType = objectTypeInspection.subTypes().stream()
                .filter(s -> name.equals(s.name())).findFirst();
        if (oSubType.isPresent()) {
            return new TypeExpression(identifier, oSubType.get().asParameterizedType(expressionContext.typeContext()),
                    Diamond.NO);
        }
        ParameterizedType parent = objectTypeInspection.parentClass();
        if (parent != null && !parent.isJavaLangObject()) {
            Expression res = findFieldOrSubType(identifier, parent.typeInfo, object, name, expressionContext);
            if (res != null) return res;
        }
        for (ParameterizedType interfaceImplemented : objectTypeInspection.interfacesImplemented()) {
            Expression res = findFieldOrSubType(identifier, interfaceImplemented.typeInfo, object, name, expressionContext);
            if (res != null) return res;
        }
        return null;
    }

}
