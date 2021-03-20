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
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;

public class Product extends BinaryOperator {

    private Product(Primitives primitives, Expression lhs, Expression rhs, ObjectFlow objectFlow) {
        super(primitives, lhs, primitives.multiplyOperatorInt, rhs, Precedence.MULTIPLICATIVE, objectFlow);
    }

    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setExpression(Product.product(evaluationContext, reLhs.value(), reRhs.value(), getObjectFlow())).build();
    }

    // we try to maintain a sum of products
    public static Expression product(EvaluationContext evaluationContext, Expression l, Expression r, ObjectFlow objectFlow) {
        Primitives primitives = evaluationContext.getPrimitives();

        if (l instanceof Numeric ln && ln.doubleValue() == 0 ||
                r instanceof Numeric rn && rn.doubleValue() == 0) {
            return new IntConstant(primitives, 0, ObjectFlow.NO_FLOW);
        }

        if (l instanceof Numeric ln && ln.doubleValue() == 1) return r;
        if (r instanceof Numeric rn && rn.doubleValue() == 1) return l;
        if (l instanceof Numeric ln && r instanceof Numeric rn)
            return IntConstant.intOrDouble(primitives, ln.doubleValue() * rn.doubleValue(), ObjectFlow.NO_FLOW);

        // any unknown lingering
        if (l.isUnknown() || r.isUnknown()) throw new UnsupportedOperationException();

        if (r instanceof Sum sum) {
            return Sum.sum(evaluationContext, product(evaluationContext, l, sum.lhs, objectFlow),
                    product(evaluationContext, l, sum.rhs, objectFlow), objectFlow);
        }
        if (l instanceof Sum sum) {
            return Sum.sum(evaluationContext,
                    product(evaluationContext, sum.lhs, r, objectFlow),
                    product(evaluationContext, sum.rhs, r, objectFlow), objectFlow);
        }
        return l.compareTo(r) < 0 ? new Product(primitives, l, r, objectFlow) : new Product(primitives, r, l, objectFlow);
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
