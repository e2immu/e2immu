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

package org.e2immu.analyser.model;

import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.annotation.NotNull;

import java.util.List;

/**
 * Translation takes place from statement, over expression, down to variable and type.
 * <p>
 * Blocks can only translate into blocks;
 * statements can translate into lists of statements.
 */
public interface TranslationMap {

    @NotNull
    Expression translateExpression(Expression expression);

    @NotNull
    MethodInfo translateMethod(MethodInfo methodInfo);

    @NotNull
    Variable translateVariable(Variable variable);

    @NotNull(content = true)
    List<Statement> translateStatement(InspectionProvider inspectionProvider, Statement statement);

    @NotNull
    Block translateBlock(InspectionProvider inspectionProvider, Block block);

    @NotNull
    ParameterizedType translateType(ParameterizedType parameterizedType);

    boolean expandDelayedWrappedExpressions();

    @NotNull
    LocalVariable translateLocalVariable(LocalVariable localVariable);

    boolean isEmpty();

    boolean hasVariableTranslations();

    // unlike in merge, in the case of ExplicitConstructorInvocation, we cannot predict which fields need their scope translating
    boolean recurseIntoScopeVariables();

    // because equality of delayed variables is based on ==
    Expression translateVariableExpressionNullIfNotTranslated(Variable variable);

    boolean translateYieldIntoReturn();
}
