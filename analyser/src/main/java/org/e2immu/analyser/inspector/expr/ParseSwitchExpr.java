/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.inspector.expr;

import com.github.javaparser.ast.expr.SwitchExpr;
import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.SwitchExpression;
import org.e2immu.analyser.model.statement.SwitchEntry;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;

import java.util.List;
import java.util.stream.Collectors;

public class ParseSwitchExpr {

    public static Expression parse(ExpressionContext expressionContext, SwitchExpr switchExpr) {
        Expression selector = expressionContext.parseExpression(switchExpr.getSelector());
        ExpressionContext newExpressionContext;
        TypeInfo enumType = expressionContext.selectorIsEnumType(selector);
        TypeInspection enumInspection = expressionContext.typeContext.getTypeInspection(enumType);
        if (enumType != null) {
            newExpressionContext = expressionContext.newVariableContext("switch-expression");
            Variable scope = new This(enumType);
            enumInspection.fields()
                    .forEach(fieldInfo -> newExpressionContext.variableContext.add(new FieldReference(fieldInfo, scope)));
        } else {
            newExpressionContext = expressionContext;
        }
        List<SwitchEntry> entries = switchExpr.getEntries()
                .stream()
                .map(entry -> newExpressionContext.switchEntry(selector, entry))
                .collect(Collectors.toList());
        // we'll need to deduce the return type from the entries.
        // if the entry is a StatementEntry, it must be either a throws, or an expression as statement
        // in the latter case, we can grab a value.
        // if the entry is a BlockEntry, we must look at the yield statement
        ParameterizedType parameterizedType = summarize(expressionContext, entries);
        return new SwitchExpression(selector, entries, parameterizedType);
    }

    private static ParameterizedType summarize(ExpressionContext expressionContext, List<SwitchEntry> entries) {
        // FIXME need code here

        // we haven't got a clue, so we return java.lang.Object
        return expressionContext.typeContext.getPrimitives().objectParameterizedType;
    }
}
