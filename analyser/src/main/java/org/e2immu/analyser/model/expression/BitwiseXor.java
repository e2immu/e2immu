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

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardReEvaluationInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;

public class BitwiseXor extends BinaryOperator {

    private BitwiseXor(Identifier identifier, Primitives primitives, Expression lhs, Expression rhs) {
        super(identifier, primitives, lhs, primitives.bitwiseAndOperatorInt(), rhs, Precedence.OR);
    }

    public EvaluationResult reEvaluate(EvaluationResult context, Map<Expression, Expression> translation, ForwardReEvaluationInfo forwardReEvaluationInfo) {
        EvaluationResult reLhs = lhs.reEvaluate(context, translation, forwardReEvaluationInfo);
        EvaluationResult reRhs = rhs.reEvaluate(context, translation, forwardReEvaluationInfo);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context).compose(reLhs, reRhs);
        return builder.setExpression(BitwiseXor.bitwiseXor(identifier, context, reLhs.value(), reRhs.value())).build();
    }

    public static Expression bitwiseXor(Identifier identifier, EvaluationResult evaluationContext, Expression l, Expression r) {
        Primitives primitives = evaluationContext.getPrimitives();
        if (l instanceof IntConstant li && r instanceof IntConstant ri)
            return new IntConstant(primitives, li.constant() ^ ri.constant());

        // any unknown lingering
        if (l.isEmpty() || r.isEmpty()) throw new UnsupportedOperationException();

        return new BitwiseXor(identifier, primitives, l, r);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_BITWISE_XOR;
    }

    @Override
    public boolean isNumeric() {
        return true;
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        Expression l = lhs.isDelayed() ? lhs.mergeDelays(causesOfDelay) : lhs;
        Expression r = rhs.isDelayed() ? rhs.mergeDelays(causesOfDelay) : rhs;
        if (l != lhs || r != rhs) return new BitwiseXor(identifier, primitives, l, r);
        return this;
    }
}
