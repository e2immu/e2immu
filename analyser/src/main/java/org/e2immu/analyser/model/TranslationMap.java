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
import java.util.Map;

/**
 * Translation takes place from statement, over expression, down to variable and type.
 * <p>
 * Blocks can only translate into blocks;
 * statements can translate into lists of statements.
 * <p>
 * If no translation takes place, the default behavior is to return the non-translated object.
 */
public interface TranslationMap {

    @NotNull
    default Expression translateExpression(Expression expression) {
        return expression;
    }

    @NotNull
    default MethodInfo translateMethod(MethodInfo methodInfo) {
        return methodInfo;
    }

    @NotNull
    default Variable translateVariable(InspectionProvider inspectionProvider, Variable variable) {
        return variable;
    }

    @NotNull(content = true)
    default List<Statement> translateStatement(InspectionProvider inspectionProvider, Statement statement) {
        return List.of(statement);
    }

    @NotNull
    default Block translateBlock(InspectionProvider inspectionProvider, Block block) {
        List<Statement> list = translateStatement(inspectionProvider, block);
        if (list.size() != 1) throw new UnsupportedOperationException();
        return (Block) list.get(0);
    }

    @NotNull
    default ParameterizedType translateType(ParameterizedType parameterizedType) {
        return parameterizedType;
    }

    @NotNull
    default LocalVariable translateLocalVariable(LocalVariable localVariable) {
        return localVariable;
    }

    default boolean isEmpty() {
        return true;
    }

    /*
     because equality of delayed variables is based on ==
     */
    default Expression translateVariableExpressionNullIfNotTranslated(Variable variable) {
        return null;
    }

    default boolean hasVariableTranslations() {
        return false;
    }

    /*
     unlike in merge, in the case of ExplicitConstructorInvocation, we cannot predict which fields need their scope translating
     */
    default boolean recurseIntoScopeVariables() {
        return false;
    }

    default boolean expandDelayedWrappedExpressions() {
        return false;
    }

    default boolean translateYieldIntoReturn() {
        return false;
    }

    default Map<? extends Variable, ? extends Variable> variables() {
        return Map.of();
    }

    default Map<? extends Expression, ? extends Expression> expressions() {
        return Map.of();
    }

    default Map<? extends Variable, ? extends Expression> variableExpressions() {
        return Map.of();
    }

    default Map<MethodInfo, MethodInfo> methods() {
        return Map.of();
    }

    default Map<? extends Statement, List<Statement>> statements() {
        return Map.of();
    }

    default Map<ParameterizedType, ParameterizedType> types() {
        return Map.of();
    }

    /*
    prevents translation of the original expression in a delayed expression, because
    that may lead to circular replacements.
     */
    default boolean translateToDelayedExpression() {
        return true;
    }
}
