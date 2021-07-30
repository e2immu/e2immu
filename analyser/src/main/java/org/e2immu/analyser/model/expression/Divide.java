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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;

public class Divide extends BinaryOperator {

    private Divide(Identifier identifier, Primitives primitives, Expression lhs, Expression rhs) {
        super(identifier, primitives, lhs, primitives.divideOperatorInt, rhs, Precedence.MULTIPLICATIVE);
    }

    public static EvaluationResult divide(Identifier identifier,
                                          EvaluationContext evaluationContext, Expression l, Expression r) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

        if (l instanceof Numeric ln && ln.doubleValue() == 0) return builder.setExpression(l).build();
        if (r instanceof Numeric rn && rn.doubleValue() == 0) {
            builder.raiseError(Message.Label.DIVISION_BY_ZERO);
            return builder.setExpression(l).build();
        }
        if (r instanceof Numeric rn && rn.doubleValue() == 1) return builder.setExpression(l).build();
        Primitives primitives = evaluationContext.getPrimitives();

        if (l instanceof IntConstant li && r instanceof IntConstant ri)
            return builder.setExpression(new IntConstant(primitives, li.constant() / ri.constant())).build();

        // any unknown lingering
        if (l.isUnknown() || r.isUnknown()) throw new UnsupportedOperationException();

        return builder.setExpression(new Divide(identifier, primitives, l, r)).build();
    }

    @Override
    public boolean isNumeric() {
        return true;
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_DIVIDE;
    }

}
