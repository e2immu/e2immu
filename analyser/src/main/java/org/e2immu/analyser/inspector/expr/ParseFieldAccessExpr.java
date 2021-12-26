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
import org.e2immu.analyser.inspector.ForwardReturnTypeInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ArrayLength;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.resolver.Resolver;

import java.util.Optional;

public class ParseFieldAccessExpr {
    public static Expression parse(ExpressionContext expressionContext,
                                   FieldAccessExpr fieldAccessExpr,
                                   ForwardReturnTypeInfo forwardReturnTypeInfo) {
        ForwardReturnTypeInfo forward = new ForwardReturnTypeInfo(null, false,
                forwardReturnTypeInfo.extra());
        Expression object = expressionContext.parseExpression(fieldAccessExpr.getScope(), forward);
        String name = fieldAccessExpr.getName().asString();

        if (object instanceof PackagePrefixExpression) {
            PackagePrefix packagePrefix = ((PackagePrefixExpression) object).packagePrefix();
            PackagePrefix combined = packagePrefix.append(name);
            if (expressionContext.typeContext().isPackagePrefix(combined)) {
                return new PackagePrefixExpression(combined);
            }
            String fullyQualifiedName = String.join(".", packagePrefix.prefix()) + "." + name;
            TypeInfo typeInfo = expressionContext.typeContext().getFullyQualified(fullyQualifiedName, true);
            ParameterizedType objectType = new ParameterizedType(typeInfo, 0);
            return new TypeExpression(objectType, Diamond.NO);
        }
        return createFieldAccess(expressionContext.typeContext(), object, name, fieldAccessExpr.getBegin().orElseThrow());
    }

    public static Expression createFieldAccess(InspectionProvider inspectionProvider,
                                               Expression object,
                                               String name,
                                               Position positionForErrorReporting) {
        ParameterizedType objectType = object.returnType();
        if (objectType.arrays > 0 && "length".equals(name)) {
            return new ArrayLength(inspectionProvider.getPrimitives(), object);
        }
        if (objectType.typeInfo != null) {
            Expression res = findFieldOrSubType(objectType.typeInfo, object, name, inspectionProvider);
            if (res == null) {
                throw new UnsupportedOperationException("Unknown field or subtype " + name + " in type "
                        + objectType.typeInfo.fullyQualifiedName + " at " + positionForErrorReporting);
            }
            return res;
        }
        throw new UnsupportedOperationException("Object type has no typeInfo? at " + positionForErrorReporting);
    }

    private static Expression findFieldOrSubType(TypeInfo typeInfo,
                                                 Expression object,
                                                 String name,
                                                 InspectionProvider inspectionProvider) {
        Optional<FieldInfo> oFieldInfo = Resolver.accessibleFieldsStream(inspectionProvider, typeInfo, typeInfo.primaryType())
                .filter(f -> name.equals(f.name)).findFirst();
        if (oFieldInfo.isPresent()) {
            return new VariableExpression(new FieldReference(inspectionProvider, oFieldInfo.get(), object));
        }
        TypeInspection objectTypeInspection = inspectionProvider.getTypeInspection(typeInfo);
        Optional<TypeInfo> oSubType = objectTypeInspection.subTypes().stream().filter(s -> name.equals(s.name())).findFirst();
        if (oSubType.isPresent()) {
            return new TypeExpression(oSubType.get().asParameterizedType(inspectionProvider), Diamond.NO);
        }
        ParameterizedType parent = objectTypeInspection.parentClass();
        if(parent != null && !Primitives.isJavaLangObject(parent)) {
            Expression res = findFieldOrSubType(parent.typeInfo, object, name, inspectionProvider);
            if(res != null) return res;
        }
        for (ParameterizedType interfaceImplemented : objectTypeInspection.interfacesImplemented()) {
            Expression res = findFieldOrSubType(interfaceImplemented.typeInfo, object, name, inspectionProvider);
            if(res != null) return res;
        }
        return null;
    }

}
