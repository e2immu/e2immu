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

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.inspector.ParameterizedTypeFactory;
import org.e2immu.analyser.inspector.VariableContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ForEachStatement;

public class ParseForEachStmt {

    public static Statement parse(ExpressionContext expressionContext, String label, ForEachStmt forEachStmt) {
        VariableContext newVariableContext = VariableContext.dependentVariableContext(expressionContext.variableContext);
        VariableDeclarationExpr vde = forEachStmt.getVariable();
        VariableDeclarator variable = vde.getVariable(0);
        String name = variable.getNameAsString();
        boolean isVar = "var".equals(variable.getType().asString());
        ParameterizedType parameterizedType;
        org.e2immu.analyser.model.Expression expression;
        if (isVar) {
            expression = expressionContext.parseExpression(forEachStmt.getIterable());
            parameterizedType = extractTypeParameterOfIterableOf(expressionContext, expression);
        } else {
            parameterizedType = ParameterizedTypeFactory.from(expressionContext.typeContext, variable.getType());
            expression = expressionContext.parseExpression(forEachStmt.getIterable());
        }
        LocalVariable localVariable = new LocalVariable.Builder()
                .setOwningType(expressionContext.owningType())
                .setName(name).setSimpleName(name)
                .setParameterizedType(parameterizedType)
                .build();
        newVariableContext.add(localVariable, expression);
        Block block = expressionContext.newVariableContextForEachLoop(newVariableContext)
                .parseBlockOrStatement(forEachStmt.getBody());
        LocalVariableCreation lvc = new LocalVariableCreation(
                Identifier.from(vde),
                expressionContext.typeContext,
                localVariable, EmptyExpression.EMPTY_EXPRESSION, isVar);
        return new ForEachStatement(Identifier.from(forEachStmt), label, lvc, expression, block);
    }

    private static ParameterizedType extractTypeParameterOfIterableOf(ExpressionContext expressionContext, Expression expression) {
        ParameterizedType type = expression.returnType();
        if (type.arrays > 0) {
            return type.copyWithOneFewerArrays();
        }
        /*
        Type.typeInfo implements or extends Iterable, or one of its super-types does.
        This method needs to return the concrete value for the type parameter of Iterable.
         */
        TypeInfo iterable = expressionContext.typeContext.typeMapBuilder.get(Iterable.class);
        ParameterizedType iterablePt = iterable.asParameterizedType(expressionContext.typeContext);
        ParameterizedType concreteSuperType = type.concreteSuperType(expressionContext.typeContext, iterablePt);
        assert concreteSuperType != null : """
                This should have been a compilation error:
                The expression in forEach must extend Iterable
                """; // keep the text block; part of tests of the analyser
        assert concreteSuperType.typeInfo == iterable;
        assert concreteSuperType.parameters.size() == 1;
        return concreteSuperType.parameters.get(0);
    }

}
