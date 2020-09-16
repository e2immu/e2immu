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

import com.github.javaparser.ast.expr.NameExpr;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.parser.ExpressionContext;

import static org.e2immu.analyser.util.Logger.LogTarget.RESOLVE;
import static org.e2immu.analyser.util.Logger.log;

public class ParseNameExpr {
    public static Expression parse(ExpressionContext expressionContext, NameExpr nameExpr) {
        String name = nameExpr.getNameAsString();
        NamedType namedType = expressionContext.typeContext.get(name, false);
        if (namedType instanceof TypeInfo) {
            return new TypeExpression(new ParameterizedType((TypeInfo) namedType, 0));
        }
        if (namedType instanceof TypeParameter) {
            throw new UnsupportedOperationException("How is this possible?");
        }
        Variable variable = expressionContext.variableContext.get(name, false);
        if (variable != null) {
            return new VariableExpression(variable);
        }
        PackagePrefix packagePrefix = new PackagePrefix(new String[]{name});
        if (expressionContext.typeContext.isPackagePrefix(packagePrefix)) {
            return new PackagePrefixExpression(packagePrefix);
        }
        throw new UnsupportedOperationException("Unknown name " + name + " at " + nameExpr.getBegin() +
                "; variable context is " + expressionContext.variableContext);
    }
}
