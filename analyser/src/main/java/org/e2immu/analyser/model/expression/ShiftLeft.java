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
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;

public class ShiftLeft extends BinaryOperator {

    private ShiftLeft(Identifier identifier, Primitives primitives, Expression lhs, Expression rhs) {
        super(identifier, primitives, lhs, primitives.leftShiftOperatorInt(), rhs, Precedence.SHIFT);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        Expression tl = lhs.translate(translationMap);
        Expression tr = rhs.translate(translationMap);
        if(tl == lhs && tr == rhs) return this;
        return new ShiftLeft(identifier, primitives, tl, tr);
    }

    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setExpression(ShiftLeft.shiftLeft(identifier, evaluationContext, reLhs.value(), reRhs.value())).build();
    }

    public static Expression shiftLeft(Identifier identifier, EvaluationContext evaluationContext, Expression l, Expression r) {
        if (l instanceof Numeric ln && ln.doubleValue() == 0) return l;
        if (r instanceof Numeric rn && rn.doubleValue() == 0) return l;

        Primitives primitives = evaluationContext.getPrimitives();
        if (l instanceof IntConstant li && r instanceof IntConstant ri)
            return new IntConstant(primitives, li.constant() << ri.constant());

        // any unknown lingering
        if (l.isUnknown() || r.isUnknown()) throw new UnsupportedOperationException();

        return new ShiftLeft(identifier, primitives, l, r);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_SHIFT_LEFT;
    }

    @Override
    public boolean isNumeric() {
        return true;
    }
}
