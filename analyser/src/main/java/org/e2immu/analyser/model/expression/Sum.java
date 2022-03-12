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
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.*;
import java.util.stream.Stream;

public class Sum extends BinaryOperator {

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression tl = lhs.translate(inspectionProvider, translationMap);
        Expression tr = rhs.translate(inspectionProvider, translationMap);
        if (tl == lhs && tr == rhs) return this;
        return new Sum(identifier, primitives, tl, tr);
    }

    private Sum(Identifier identifier, Primitives primitives, Expression lhs, Expression rhs) {
        super(identifier, primitives, lhs, primitives.plusOperatorInt(), rhs, Precedence.ADDITIVE);
    }

    public EvaluationResult reEvaluate(EvaluationResult context, Map<Expression, Expression> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(context, translation);
        EvaluationResult reRhs = rhs.reEvaluate(context, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context).compose(reLhs, reRhs);
        return builder.setExpression(Sum.sum(identifier, context, reLhs.getExpression(), reRhs.getExpression())).build();
    }

    // we try to maintain a sum of products
    public static Expression sum(EvaluationResult evaluationContext, Expression l, Expression r) {
        CausesOfDelay causes = l.causesOfDelay().merge(r.causesOfDelay());
        Identifier identifier = Identifier.joined("sum", List.of(l.getIdentifier(), r.getIdentifier()));
        Expression expression = sum(identifier,
                evaluationContext, l, r, true);
        return causes.isDelayed() && !expression.isDelayed() ? DelayedExpression.forSimplification(identifier, expression.returnType(), causes) : expression;
    }

    public static Expression sum(Identifier identifier, EvaluationResult evaluationContext, Expression l, Expression r) {
        CausesOfDelay causes = l.causesOfDelay().merge(r.causesOfDelay());
        Expression expression = sum(identifier, evaluationContext, l, r, true);
        return causes.isDelayed() && !expression.isDelayed() ? DelayedExpression.forSimplification(identifier, expression.returnType(), causes) : expression;
    }

    private static Expression sum(Identifier identifier,
                                  EvaluationResult evaluationContext, Expression l, Expression r, boolean tryAgain) {
        Primitives primitives = evaluationContext.getPrimitives();

        if (l.equals(r)) return Product.product(identifier, evaluationContext, new IntConstant(primitives, 2), l);
        if (l instanceof IntConstant li && li.constant() == 0) return r;
        if (r instanceof IntConstant ri && ri.constant() == 0) return l;
        if (l instanceof Negation ln && ln.expression.equals(r) ||
                r instanceof Negation rn && rn.expression.equals(l)) {
            return new IntConstant(primitives, 0);
        }
        if (l instanceof Numeric ln && r instanceof Numeric rn)
            return IntConstant.intOrDouble(primitives, ln.doubleValue() + rn.doubleValue());

        // similar code in Equals (common terms)

        Expression[] terms = Stream.concat(expandTerms(evaluationContext, l, false),
                expandTerms(evaluationContext, r, false)).toArray(Expression[]::new);
        Arrays.sort(terms);
        Expression[] termsOfProducts = makeProducts(evaluationContext, terms);
        if (termsOfProducts.length == 0) return new IntConstant(primitives, 0);
        if (termsOfProducts.length == 1) return termsOfProducts[0];
        Expression newL, newR;
        if (termsOfProducts.length == 2) {
            newL = termsOfProducts[0];
            newR = termsOfProducts[1];
        } else {
            newL = wrapInSum(evaluationContext, termsOfProducts, termsOfProducts.length - 1);
            newR = termsOfProducts[termsOfProducts.length - 1];
        }

        Identifier id = Identifier.joined("sum", List.of(newL.getIdentifier(), newR.getIdentifier()));
        Sum s = new Sum(id, primitives, newL, newR);

        // re-running the sum to solve substitutions of variables to constants
        if (tryAgain) {
            return Sum.sum(identifier, evaluationContext, s.lhs, s.rhs, false);
        }

        return s;
    }

    static Expression[] makeProducts(EvaluationResult evaluationContext, Expression[] terms) {
        Primitives primitives = evaluationContext.getPrimitives();
        List<Expression> result = new ArrayList<>(terms.length);
        int pos = 1;
        result.add(terms[0]); // set the first
        while (pos < terms.length) {
            Expression e = terms[pos];
            int latestIndex = result.size() - 1;
            Expression latest = result.get(latestIndex);
            if (e instanceof Numeric n1 && latest instanceof Numeric n2) {
                Expression sum = IntConstant.intOrDouble(primitives, n1.doubleValue() + n2.doubleValue());
                result.set(latestIndex, sum);
            } else {
                Factor f1 = getFactor(latest);
                Factor f2 = getFactor(e);
                if (f1.term.equals(f2.term)) {
                    if (f1.factor == -f2.factor) {
                        result.set(latestIndex, new IntConstant(primitives, 0));
                    } else {
                        Expression f = IntConstant.intOrDouble(primitives, f1.factor + f2.factor);
                        Expression product = Product.product(evaluationContext, f, f1.term);
                        result.set(latestIndex, product);
                    }
                } else {
                    result.add(e);
                }
            }
            pos++;
        }
        Collections.sort(result);
        result.removeIf(e -> e instanceof Numeric n && n.doubleValue() == 0);
        return result.toArray(Expression[]::new);
    }

    private record Factor(double factor, Expression term) {
    }

    private static Factor getFactor(Expression term) {
        if (term instanceof Negation neg) {
            Factor f = getFactor(neg.expression);
            return new Factor(-f.factor, f.term);
        }
        if (term instanceof Product p && p.lhs instanceof Numeric n) {
            return new Factor(n.doubleValue(), p.rhs);
        }
        return new Factor(1, term);
    }


    // we have more than 2 terms, that's a sum of sums...
    static Expression wrapInSum(EvaluationResult evaluationContext, Expression[] expressions, int i) {
        assert i >= 2;
        if (i == 2) return Sum.sum(evaluationContext, expressions[0], expressions[1]);
        return Sum.sum(evaluationContext, wrapInSum(evaluationContext, expressions, i - 1), expressions[i - 1]);
    }

    public static Stream<Expression> expandTerms(EvaluationResult evaluationContext, Expression expression, boolean negate) {
        if (expression instanceof Sum sum) {
            return Stream.concat(expandTerms(evaluationContext, sum.lhs, negate),
                    expandTerms(evaluationContext, sum.rhs, negate));
        }
        if (negate) {
            return Stream.of(Negation.negate(evaluationContext, expression));
        }
        return Stream.of(expression);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_SUM;
    }

    // -(lhs + rhs) = -lhs + -rhs
    public Expression negate(EvaluationResult evaluationContext) {
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

    public Expression isZero(EvaluationResult evaluationContext) {
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

    // recursive method
    public Double numericPartOfLhs() {
        if (lhs instanceof Numeric n) return n.doubleValue();
        if (lhs instanceof Sum s) return s.numericPartOfLhs();
        return null;
    }

    // can only be called when there is a numeric part somewhere!
    public Expression nonNumericPartOfLhs(EvaluationResult evaluationContext) {
        if (lhs instanceof Numeric) return rhs;
        if (lhs instanceof Sum s) {
            // the numeric part is somewhere inside lhs
            Expression nonNumeric = s.nonNumericPartOfLhs(evaluationContext);
            return Sum.sum(evaluationContext, nonNumeric, rhs);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        Expression l = lhs.isDelayed() ? lhs.mergeDelays(causesOfDelay) : lhs;
        Expression r = rhs.isDelayed() ? rhs.mergeDelays(causesOfDelay) : rhs;
        if (l != lhs || r != rhs) return new Sum(identifier, primitives, l, r);
        return this;
    }
}
