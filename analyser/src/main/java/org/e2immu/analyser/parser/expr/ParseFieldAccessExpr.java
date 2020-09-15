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

import com.github.javaparser.Position;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ArrayLengthExpression;
import org.e2immu.analyser.model.expression.FieldAccess;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.parser.ExpressionContext;

import java.util.List;
import java.util.Optional;

import static org.e2immu.analyser.util.Logger.LogTarget.RESOLVE;
import static org.e2immu.analyser.util.Logger.log;

public class ParseFieldAccessExpr {
    public static Expression parse(ExpressionContext expressionContext, FieldAccessExpr fieldAccessExpr) {
        Expression object = expressionContext.parseExpression(fieldAccessExpr.getScope());
        String name = fieldAccessExpr.getName().asString();

        if (object instanceof PackagePrefixExpression) {
            PackagePrefix packagePrefix = ((PackagePrefixExpression) object).packagePrefix;
            PackagePrefix combined = packagePrefix.append(name);
            if (expressionContext.typeContext.isPackagePrefix(combined)) {
                return new PackagePrefixExpression(combined);
            }
            String fullyQualifiedName = String.join(".", packagePrefix.prefix) + "." + name;
            TypeInfo typeInfo = expressionContext.typeContext.getFullyQualified(fullyQualifiedName, true);
            ParameterizedType objectType = new ParameterizedType(typeInfo, 0);
            expressionContext.dependenciesOnOtherTypes.addAll(objectType.typeInfoSet());
            return new TypeExpression(objectType);
        }
        return createFieldAccess(expressionContext, object, name, fieldAccessExpr.getBegin().orElseThrow());
    }

    public static Expression createFieldAccess(ExpressionContext expressionContext, Expression object, String name, Position positionForErrorReporting) {
        ParameterizedType objectType = object.returnType();
        expressionContext.dependenciesOnOtherTypes.addAll(objectType.typeInfoSet());
        if (objectType.arrays > 0 && "length".equals(name)) return new ArrayLengthExpression(object);

        if (objectType.typeInfo != null) {
            Optional<FieldInfo> oFieldInfo = objectType.typeInfo.accessibleFieldsStream().filter(f -> name.equals(f.name)).findFirst();
            if (oFieldInfo.isPresent()) {
                return fieldAccess(expressionContext, oFieldInfo.get(), object);
            }
            Optional<TypeInfo> oSubType = objectType.typeInfo.typeInspection.getPotentiallyRun().subTypes.stream().filter(s -> name.equals(s.name())).findFirst();
            if (oSubType.isPresent()) {
                return new TypeExpression(oSubType.get().asParameterizedType());
            }
            throw new UnsupportedOperationException("Unknown field or subtype " + name + " in type " + objectType.typeInfo.fullyQualifiedName + " at " + positionForErrorReporting);
        } else {
            throw new UnsupportedOperationException("Object type has no typeInfo? at " + positionForErrorReporting);
        }
    }

    private static FieldAccess fieldAccess(ExpressionContext expressionContext, FieldInfo fieldInfo, Expression object) {
        if (fieldInfo.owner == expressionContext.enclosingType) {
            log(RESOLVE, "Adding dependency on field {}", fieldInfo.fullyQualifiedName());
            expressionContext.dependenciesOnOtherMethodsAndFields.add(fieldInfo);
        }
        // in a static context, the object is a type expression.
        // it can be a method call, such as findNode().data (data is the field)
        // Otherwise, it has to be a variable
        List<Variable> vars = object.variables();
        Variable objectVariable = vars.isEmpty() ? null : vars.get(0);
        FieldReference fieldReference = new FieldReference(fieldInfo, objectVariable);
        return new FieldAccess(object, fieldReference);
    }
}
