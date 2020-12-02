/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.value.NegatedValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;
import java.util.Objects;

public class Sum extends BinaryOperator {
    private final Primitives primitives;

    private Sum(Primitives primitives, Expression lhs, Expression rhs, ObjectFlow objectFlow) {
        super(lhs, primitives.plusOperatorInt, rhs, BinaryOperator.ADDITIVE_PRECEDENCE, objectFlow);
        this.primitives = primitives;
    }

    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setExpression(Sum.sum(evaluationContext, reLhs.getExpression(), reRhs.getExpression(), getObjectFlow())).build();
    }

    // we try to maintain a sum of products
    public static Expression sum(EvaluationContext evaluationContext, Expression l, Expression r, ObjectFlow objectFlow) {
        Primitives primitives = evaluationContext.getPrimitives();

        if (l.equals(r)) return Product.product(evaluationContext,
                new IntConstant(primitives, 2, ObjectFlow.NO_FLOW), l, objectFlow);
        if (l instanceof IntConstant li && li.constant() == 0) return r;
        if (r instanceof IntConstant ri && ri.constant() == 0) return l;
        if (l instanceof NegatedValue && ((NegatedValue) l).value.equals(r) ||
                r instanceof NegatedValue && ((NegatedValue) r).value.equals(l)) {
            return new IntConstant(primitives, 0, ObjectFlow.NO_FLOW);
        }
        if (l instanceof Numeric ln && r instanceof Numeric rn)
            return IntConstant.intOrDouble(primitives, ln.doubleValue() + rn.doubleValue(), objectFlow);

        // any unknown lingering
        if (l.isUnknown() || r.isUnknown()) return EmptyExpression.UNKNOWN_PRIMITIVE;

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
        return l.compareTo(r) < 0 ? new Sum(primitives, l, r, objectFlow) : new Sum(primitives, r, l, objectFlow);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sum orValue = (Sum) o;
        return lhs.equals(orValue.lhs) &&
                rhs.equals(orValue.rhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs);
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return "(" + lhs.print(printMode) + " + " + rhs.print(printMode) + ")";
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_SUM;
    }

    // -(lhs + rhs) = -lhs + -rhs
    public Expression negate(EvaluationContext evaluationContext) {
        return Sum.sum(evaluationContext,
                NegatedExpression.negate(evaluationContext, lhs),
                NegatedExpression.negate(evaluationContext, rhs), getObjectFlow());
    }

    @Override
    public ParameterizedType type() {
        return primitives.widestType(lhs.type(), rhs.type());
    }

    @Override
    public boolean isNumeric() {
        return true;
    }
}
