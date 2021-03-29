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
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;

public class Product extends BinaryOperator {

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new Product(primitives, lhs.translate(translationMap), rhs.translate(translationMap));
    }

    private Product(Primitives primitives, Expression lhs, Expression rhs) {
        super(primitives, lhs, primitives.multiplyOperatorInt, rhs, Precedence.MULTIPLICATIVE);
    }

    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setExpression(Product.product(evaluationContext, reLhs.value(), reRhs.value())).build();
    }

    // we try to maintain a sum of products
    public static Expression product(EvaluationContext evaluationContext, Expression l, Expression r) {
        Primitives primitives = evaluationContext.getPrimitives();

        if (l instanceof Numeric ln && ln.doubleValue() == 0 ||
                r instanceof Numeric rn && rn.doubleValue() == 0) {
            return new IntConstant(primitives, 0);
        }

        if (l instanceof Numeric ln && ln.doubleValue() == 1) return r;
        if (r instanceof Numeric rn && rn.doubleValue() == 1) return l;
        if (l instanceof Numeric ln && r instanceof Numeric rn)
            return IntConstant.intOrDouble(primitives, ln.doubleValue() * rn.doubleValue());

        // any unknown lingering
        if (l.isUnknown() || r.isUnknown()) throw new UnsupportedOperationException();

        if (r instanceof Sum sum) {
            return Sum.sum(evaluationContext, product(evaluationContext, l, sum.lhs),
                    product(evaluationContext, l, sum.rhs));
        }
        if (l instanceof Sum sum) {
            return Sum.sum(evaluationContext,
                    product(evaluationContext, sum.lhs, r),
                    product(evaluationContext, sum.rhs, r));
        }
        return l.compareTo(r) < 0 ? new Product(primitives, l, r) : new Product(primitives, r, l);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_PRODUCT;
    }

    @Override
    public boolean isNumeric() {
        return true;
    }
}
