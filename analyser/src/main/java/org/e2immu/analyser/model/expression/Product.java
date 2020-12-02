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
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;
import java.util.Objects;

public class Product extends BinaryOperator {
    private final Primitives primitives;

    private Product(Primitives primitives, Expression lhs, Expression rhs, ObjectFlow objectFlow) {
        super(lhs, primitives.multiplyOperatorInt, rhs, BinaryOperator.MULTIPLICATIVE_PRECEDENCE, objectFlow);
        this.primitives = primitives;
    }

    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setExpression(Product.product(evaluationContext, reLhs.value, reRhs.value, getObjectFlow())).build();
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
        if (l.isUnknown() || r.isUnknown()) return EmptyExpression.UNKNOWN_PRIMITIVE;

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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product orValue = (Product) o;
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
        return lhs.print(printMode) + " * " + rhs.print(printMode);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_PRODUCT;
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
