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
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;

public class Sum extends BinaryOperator {

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new Sum(primitives, lhs.translate(translationMap), rhs.translate(translationMap), objectFlow);
    }

    private Sum(Primitives primitives, Expression lhs, Expression rhs, ObjectFlow objectFlow) {
        super(primitives, lhs, primitives.plusOperatorInt, rhs, Precedence.ADDITIVE, objectFlow);
    }

    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setExpression(Sum.sum(evaluationContext, reLhs.getExpression(), reRhs.getExpression(), getObjectFlow())).build();
    }

    // we try to maintain a sum of products
    public static Expression sum(EvaluationContext evaluationContext, Expression l, Expression r, ObjectFlow objectFlow) {
        return sum(evaluationContext, l, r, objectFlow, true);
    }

    private static Expression sum(EvaluationContext evaluationContext, Expression l, Expression r, ObjectFlow objectFlow, boolean tryAgain) {
        Primitives primitives = evaluationContext.getPrimitives();

        if (l.equals(r)) return Product.product(evaluationContext,
                new IntConstant(primitives, 2, ObjectFlow.NO_FLOW), l, objectFlow);
        if (l instanceof IntConstant li && li.constant() == 0) return r;
        if (r instanceof IntConstant ri && ri.constant() == 0) return l;
        if (l instanceof Negation ln && ln.expression.equals(r) ||
                r instanceof Negation rn && rn.expression.equals(l)) {
            return new IntConstant(primitives, 0, ObjectFlow.NO_FLOW);
        }
        if (l instanceof Numeric ln && r instanceof Numeric rn)
            return IntConstant.intOrDouble(primitives, ln.doubleValue() + rn.doubleValue(), objectFlow);

        // any unknown lingering
        //if (l.isUnknown() || r.isUnknown()) throw new UnsupportedOperationException();

        // a + (b+c)
        if (l instanceof Numeric ln && r instanceof Sum s && s.lhs instanceof Numeric l2) {
            return Sum.sum(evaluationContext, IntConstant.intOrDouble(primitives, ln.doubleValue() + l2.doubleValue(), s.objectFlow),
                    s.rhs, s.objectFlow);
        }

        // a + x*a
        if (l instanceof Product lp && lp.lhs instanceof Numeric lpLn && r.equals(lp.rhs))
            return Product.product(evaluationContext,
                    IntConstant.intOrDouble(primitives, 1 + lpLn.doubleValue(),
                            lp.lhs.getObjectFlow()), r, objectFlow);
        if (r instanceof Product rp && rp.lhs instanceof Numeric rpLn && l.equals(rp.rhs))
            return Product.product(evaluationContext, IntConstant.intOrDouble(primitives,
                    1 + rpLn.doubleValue(), rp.lhs.getObjectFlow()), l, objectFlow);

        // n*a + m*a
        if (l instanceof Product lp && r instanceof Product rp &&
                lp.lhs instanceof Numeric lpLn && rp.lhs instanceof Numeric rpLn &&
                lp.rhs.equals(rp.rhs)) {
            return Product.product(evaluationContext,
                    IntConstant.intOrDouble(primitives, lpLn.doubleValue() + rpLn.doubleValue(), objectFlow),
                    lp.rhs, objectFlow);
        }
        Sum s = l.compareTo(r) < 0 ? new Sum(primitives, l, r, objectFlow) : new Sum(primitives, r, l, objectFlow);

        // re-running the sum to solve substitutions of variables to constants
        if (tryAgain) {
            return Sum.sum(evaluationContext, s.lhs, s.rhs, s.objectFlow, false);
        }

        return s;
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_SUM;
    }

    // -(lhs + rhs) = -lhs + -rhs
    public Expression negate(EvaluationContext evaluationContext) {
        return Sum.sum(evaluationContext,
                Negation.negate(evaluationContext, lhs),
                Negation.negate(evaluationContext, rhs), getObjectFlow());
    }

    @Override
    public boolean isNumeric() {
        return true;
    }


    @Override
    public OutputBuilder output(Qualification qualification) {
        OutputBuilder outputBuilder = new OutputBuilder().add(outputInParenthesis(qualification, precedence(), lhs));
        boolean ignoreOperator = rhs instanceof Negation || rhs instanceof Sum sum2 && (sum2.lhs instanceof Negation);
        if (!ignoreOperator) {
            outputBuilder.add(Symbol.binaryOperator(operator.name));
        }
        return outputBuilder.add(outputInParenthesis(qualification, precedence(), rhs));
    }

}
