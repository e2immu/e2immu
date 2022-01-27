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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;

public abstract class CommonVariableExpression extends BaseExpression implements IsVariableExpression {

    protected CommonVariableExpression(Identifier identifier) {
        super(identifier);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_VARIABLE;
    }

    @Override
    public int internalCompareTo(Expression v) {
        InlineConditional ic;
        Expression e;
        if ((ic = v.asInstanceOf(InlineConditional.class)) != null) {
            e = ic.condition;
        } else e = v;
        IsVariableExpression ive;
        if ((ive = e.asInstanceOf(IsVariableExpression.class)) != null) {
            // compare variables
            return variableId().compareTo(ive.variableId());
        } else throw new UnsupportedOperationException();
    }
}
