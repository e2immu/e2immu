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
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.value.BitwiseAndValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;
import java.util.Objects;

public class BitwiseAnd extends BinaryOperator {
    private final Primitives primitives;

    private BitwiseAnd(Primitives primitives, Expression lhs, Expression rhs, ObjectFlow objectFlow) {
        super(lhs, primitives.bitwiseAndOperatorInt, rhs, BinaryOperator.AND_PRECEDENCE, objectFlow);
        this.primitives = primitives;
    }

    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setExpression(BitwiseAnd.bitwiseAnd(evaluationContext, reLhs.value, reRhs.value, getObjectFlow())).build();
    }

    // we try to maintain a sum of products
    public static Expression bitwiseAnd(EvaluationContext evaluationContext, Expression l, Expression r, ObjectFlow objectFlow) {
        if (l instanceof Numeric ln && ln.doubleValue() == 0) return l;
        if (r instanceof Numeric rn && rn.doubleValue() == 0) return r;
        if (r instanceof Numeric rn && rn.doubleValue() == 1) return l;
        Primitives primitives = evaluationContext.getPrimitives();
        if (l instanceof IntConstant li && r instanceof IntConstant ri)
            return new IntConstant(primitives, li.constant() & ri.constant(), objectFlow);

        // any unknown lingering
        if (l.isUnknown() || r.isUnknown()) return PrimitiveExpression.PRIMITIVE_EXPRESSION;

        return new BitwiseAnd(primitives, l, r, objectFlow);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return PrimitiveExpression.primitiveGetProperty(variableProperty);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BitwiseAnd orValue = (BitwiseAnd) o;
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
        return lhs.print(printMode) + " & " + rhs.print(printMode);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_BITWISE_AND;
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
