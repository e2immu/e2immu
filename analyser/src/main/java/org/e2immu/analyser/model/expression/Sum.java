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
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.Primitives;

import java.util.Arrays;
import java.util.Map;

public class Sum extends BinaryOperator {

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new Sum(identifier, primitives, lhs.translate(translationMap), rhs.translate(translationMap));
    }

    private Sum(Identifier identifier, Primitives primitives, Expression lhs, Expression rhs) {
        super(identifier, primitives, lhs, primitives.plusOperatorInt(), rhs, Precedence.ADDITIVE);
    }

    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setExpression(Sum.sum(identifier, evaluationContext, reLhs.getExpression(), reRhs.getExpression())).build();
    }

    // we try to maintain a sum of products
    public static Expression sum(EvaluationContext evaluationContext, Expression l, Expression r) {
        return sum(Identifier.generate(), evaluationContext, l, r, true);
    }

    public static Expression sum(Identifier identifier, EvaluationContext evaluationContext, Expression l, Expression r) {
        return sum(identifier, evaluationContext, l, r, true);
    }

    private static Expression sum(Identifier identifier,
                                  EvaluationContext evaluationContext, Expression l, Expression r, boolean tryAgain) {
        Primitives primitives = evaluationContext.getPrimitives();

        if (l.equals(r)) return Product.product(evaluationContext, new IntConstant(primitives, 2), l);
        if (l instanceof IntConstant li && li.constant() == 0) return r;
        if (r instanceof IntConstant ri && ri.constant() == 0) return l;
        if (l instanceof Negation ln && ln.expression.equals(r) ||
                r instanceof Negation rn && rn.expression.equals(l)) {
            return new IntConstant(primitives, 0);
        }
        if (l instanceof Numeric ln && r instanceof Numeric rn)
            return IntConstant.intOrDouble(primitives, ln.doubleValue() + rn.doubleValue());

        // any unknown lingering
        //if (l.isUnknown() || r.isUnknown()) throw new UnsupportedOperationException();

        // a + (b+c)
        if (l instanceof Numeric ln && r instanceof Sum s && s.lhs instanceof Numeric l2) {
            return Sum.sum(identifier, evaluationContext,
                    IntConstant.intOrDouble(primitives, ln.doubleValue() + l2.doubleValue()), s.rhs);
        }

        // (a+b) + (c+d)
        if (l instanceof Sum s1 && r instanceof Sum s2) {
            Expression[] expressions = new Expression[]{s1.lhs, s1.rhs, s2.lhs, s2.rhs};
            Arrays.sort(expressions);
            if (!s1.lhs.equals(expressions[0]) || !s1.rhs.equals(expressions[1])) {
                return Sum.sum(identifier, evaluationContext, Sum.sum(evaluationContext, expressions[0], expressions[1]),
                        Sum.sum(evaluationContext, expressions[2], expressions[3]));
            }
        }
        // a + x*a
        if (l instanceof Product lp && lp.lhs instanceof Numeric lpLn && r.equals(lp.rhs))
            return Product.product(evaluationContext,
                    IntConstant.intOrDouble(primitives, 1 + lpLn.doubleValue()), r);
        if (r instanceof Product rp && rp.lhs instanceof Numeric rpLn && l.equals(rp.rhs))
            return Product.product(evaluationContext, IntConstant.intOrDouble(primitives,
                    1 + rpLn.doubleValue()), l);

        // n*a + m*a
        if (l instanceof Product lp && r instanceof Product rp &&
                lp.lhs instanceof Numeric lpLn && rp.lhs instanceof Numeric rpLn &&
                lp.rhs.equals(rp.rhs)) {
            return Product.product(evaluationContext,
                    IntConstant.intOrDouble(primitives, lpLn.doubleValue() + rpLn.doubleValue()), lp.rhs);
        }

        Sum s = l.compareTo(r) < 0 ? new Sum(Identifier.generate(), primitives, l, r)
                : new Sum(Identifier.generate(), primitives, r, l);

        // re-running the sum to solve substitutions of variables to constants
        if (tryAgain) {
            return Sum.sum(identifier, evaluationContext, s.lhs, s.rhs, false);
        }

        return s;
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_SUM;
    }

    // -(lhs + rhs) = -lhs + -rhs
    public Expression negate(EvaluationContext evaluationContext) {
        return Sum.sum(identifier, evaluationContext,
                Negation.negate(evaluationContext, lhs),
                Negation.negate(evaluationContext, rhs));
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

    public Expression isZero(EvaluationContext evaluationContext) {
        if (lhs instanceof Negation negation && !(rhs instanceof Negation)) {
            return Equals.equals(evaluationContext, negation.expression, rhs);
        }
        if (rhs instanceof Negation negation && !(lhs instanceof Negation)) {
            return Equals.equals(evaluationContext, lhs, negation.expression);
        }
        if (lhs instanceof Negation nLhs) {
            return Equals.equals(evaluationContext, nLhs, rhs);
        }
        return Equals.equals(evaluationContext, lhs, Negation.negate(evaluationContext, rhs));
    }

    @Override
    public Expression removeAllReturnValueParts() {
        boolean removeLhs = lhs.isReturnValue();
        boolean removeRhs = rhs.isReturnValue();
        if (removeLhs && removeRhs) return lhs; // nothing we can do
        if (removeLhs) return rhs;
        if (removeRhs) return lhs;
        return new Sum(identifier, primitives, lhs.removeAllReturnValueParts(), rhs.removeAllReturnValueParts());
    }
}
