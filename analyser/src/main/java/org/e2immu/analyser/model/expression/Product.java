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
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.List;

public class Product extends BinaryOperator {

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression tl = lhs.translate(inspectionProvider, translationMap);
        Expression tr = rhs.translate(inspectionProvider, translationMap);
        if (tl == lhs && tr == rhs) return this;
        return new Product(identifier, primitives, tl, tr);
    }

    private Product(Identifier identifier, Primitives primitives, Expression lhs, Expression rhs) {
        super(identifier, primitives, lhs, primitives.multiplyOperatorInt(), rhs, Precedence.MULTIPLICATIVE);
    }

    public static Expression product(EvaluationResult evaluationContext, Expression l, Expression r) {
        Identifier id = Identifier.joined("product", List.of(l.getIdentifier(), r.getIdentifier()));
        return product(id, evaluationContext, l, r);
    }

    public static Expression product(Identifier identifier, EvaluationResult evaluationContext, Expression l, Expression r) {
        CausesOfDelay causes = l.causesOfDelay().merge(r.causesOfDelay());
        Expression expression = internalProduct(identifier, evaluationContext, l, r);
        return causes.isDelayed() && expression.isDone()
                ? DelayedExpression.forSimplification(identifier, expression.returnType(),
                expression.variables(true), causes)
                : expression;
    }

    // we try to maintain a sum of products
    private static Expression internalProduct(Identifier identifier, EvaluationResult evaluationContext, Expression l, Expression r) {
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
        if (l.isEmpty() || r.isEmpty()) throw new UnsupportedOperationException();

        if (r instanceof Sum sum) {
            return Sum.sum(identifier, evaluationContext, product(evaluationContext, l, sum.lhs),
                    product(evaluationContext, l, sum.rhs));
        }
        if (l instanceof Sum sum) {
            return Sum.sum(identifier, evaluationContext,
                    product(evaluationContext, sum.lhs, r),
                    product(evaluationContext, sum.rhs, r));
        }
        return l.compareTo(r) < 0 ? new Product(identifier, primitives, l, r) : new Product(identifier, primitives, r, l);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_PRODUCT;
    }

    @Override
    public boolean isNumeric() {
        return true;
    }

    @Override
    public Expression removeAllReturnValueParts(Primitives primitives) {
        Expression l = lhs.removeAllReturnValueParts(primitives);
        Expression r = rhs.removeAllReturnValueParts(primitives);
        if (l == null && r == null) return new IntConstant(primitives, 1);
        if (l == null) return r;
        if (r == null) return l;
        return new Product(identifier, primitives, l, r);
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        Expression l = lhs.isDelayed() ? lhs.mergeDelays(causesOfDelay) : lhs;
        Expression r = rhs.isDelayed() ? rhs.mergeDelays(causesOfDelay) : rhs;
        if (l != lhs || r != rhs) return new Product(identifier, primitives, l, r);
        return this;
    }
}
