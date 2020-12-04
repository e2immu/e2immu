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
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;

public class BitwiseAnd extends BinaryOperator {

    private BitwiseAnd(Primitives primitives, Expression lhs, Expression rhs, ObjectFlow objectFlow) {
        super(primitives, lhs, primitives.bitwiseAndOperatorInt, rhs, BinaryOperator.AND_PRECEDENCE, objectFlow);
    }

    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setExpression(BitwiseAnd.bitwiseAnd(evaluationContext, reLhs.value, reRhs.value, getObjectFlow())).build();
    }

    // we try to maintain a sum of products
    public static Expression bitwiseAnd(EvaluationContext evaluationContext, Expression l, Expression r, ObjectFlow objectFlow) {
        assert objectFlow != ObjectFlow.NYE;
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
    public int order() {
        return ExpressionComparator.ORDER_BITWISE_AND;
    }

    @Override
    public boolean isNumeric() {
        return true;
    }
}
