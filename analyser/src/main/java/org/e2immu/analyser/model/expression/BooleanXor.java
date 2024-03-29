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

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.parser.Primitives;

public class BooleanXor extends BinaryOperator {

    private BooleanXor(Identifier identifier, Primitives primitives, Expression lhs, Expression rhs) {
        super(identifier, primitives, lhs, primitives.bitwiseAndOperatorInt(), rhs, Precedence.XOR);
    }

    public static Expression booleanXor(Identifier identifier, EvaluationResult evaluationContext, Expression l, Expression r) {
        Primitives primitives = evaluationContext.getPrimitives();
        if (l instanceof BooleanConstant li && r instanceof BooleanConstant ri)
            return new BooleanConstant(primitives, li.constant() ^ ri.constant());

        // any unknown lingering
        if (l.isEmpty() || r.isEmpty()) throw new UnsupportedOperationException();

        return new BooleanXor(identifier, primitives, l, r);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_BOOLEAN_XOR;
    }

    @Override
    public boolean isNumeric() {
        return true;
    }
}
