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

package org.e2immu.analyser.inspector;

import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.SwitchEntry;
import org.e2immu.analyser.resolver.Resolver;
import org.e2immu.annotation.NotNull;

public interface ExpressionContext {

    ExpressionContext newVariableContext(MethodInfo methodInfo, ForwardReturnTypeInfo forwardReturnTypeInfo);

    ExpressionContext newVariableContext(@NotNull String reason);

    ExpressionContext newVariableContextForEachLoop(@NotNull VariableContext newVariableContext);

    ExpressionContext newSwitchExpressionContext(ForwardReturnTypeInfo typeOfEnclosingSwitchExpression);

    ExpressionContext newLambdaContext(TypeInfo subType, VariableContext variableContext);

    ExpressionContext newSubType(@NotNull TypeInfo subType);

    ExpressionContext newTypeContext(String reason);

    ExpressionContext newTypeContext(FieldInfo fieldInfo);

    /*
        used for compact constructors: we have already added some synthetic statements
         */
    Block continueParsingBlock(BlockStmt blockStmt, Block.BlockBuilder blockBuilder);

    Block parseBlockOrStatement(Statement stmt);

    TypeInfo selectorIsEnumType(@NotNull Expression selector);

    SwitchEntry switchEntry(Expression switchVariableAsExpression,
                            @NotNull com.github.javaparser.ast.stmt.SwitchEntry switchEntry);

    @NotNull
    Expression parseExpressionStartVoid(@NotNull com.github.javaparser.ast.expr.Expression expression);

    Expression parseExpression(@NotNull com.github.javaparser.ast.expr.Expression expression,
                               ForwardReturnTypeInfo forwardReturnTypeInfo);

    TypeInfo owningType();

    Location getLocation();

    TypeInfo enclosingType();

    TypeInfo primaryType();

    TypeContext typeContext();

    VariableContext variableContext();

    AnonymousTypeCounters anonymousTypeCounters();

    Resolver resolver();
}
