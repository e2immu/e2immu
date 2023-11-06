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

import com.github.javaparser.ast.expr.NameExpr;
import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.Variable;

public class ParseNameExpr {
    public static Expression parse(ExpressionContext expressionContext, NameExpr nameExpr) {
        return parse(expressionContext, nameExpr.getNameAsString(), Identifier.from(nameExpr));
    }

    public static Expression parse(ExpressionContext expressionContext, String name, Identifier identifier) {
        Variable variable = expressionContext.variableContext().get(name, false);
        if (variable != null) {
            return new VariableExpression(identifier, variable);
        }
        NamedType namedType = expressionContext.typeContext().get(name, false);
        if (namedType instanceof TypeInfo) {
            ParameterizedType parameterizedType = new ParameterizedType((TypeInfo) namedType, 0);
            return new TypeExpression(identifier, parameterizedType, Diamond.SHOW_ALL);
        }
        if (namedType instanceof TypeParameter) {
            throw new UnsupportedOperationException("How is this possible?");
        }
        PackagePrefix packagePrefix = new PackagePrefix(new String[]{name});
        if (expressionContext.typeContext().isPackagePrefix(packagePrefix)) {
            return new PackagePrefixExpression(packagePrefix);
        }
        throw new UnsupportedOperationException("Unknown name " + name + " at " + identifier +
                "; variable context is " + expressionContext.variableContext());
    }
}
