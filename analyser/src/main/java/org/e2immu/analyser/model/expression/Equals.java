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
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.*;

public class Equals extends BinaryOperator {

    // public for testing
    public Equals(Primitives primitives,
                  Expression lhs, Expression rhs, ObjectFlow objectFlow) {
        super(primitives, lhs, lhs.isNumeric() ? primitives.equalsOperatorInt : primitives.equalsOperatorObject,
                rhs, Precedence.EQUALITY, objectFlow);
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setExpression(Equals.equals(evaluationContext, reLhs.value(), reRhs.value(), objectFlow)).build();
    }

    public static Expression equals(EvaluationContext evaluationContext, Expression l, Expression r, ObjectFlow objectFlow) {
        Primitives primitives = evaluationContext.getPrimitives();
        if (l.equals(r)) return new BooleanConstant(primitives, true, objectFlow);
        if (l.isUnknown() || r.isUnknown()) return l.combineUnknown(r);

        if (l instanceof NullConstant && evaluationContext.isNotNull0(r) ||
                r instanceof NullConstant && evaluationContext.isNotNull0(l))
            return new BooleanConstant(primitives, false, objectFlow);

        if (l instanceof ConstantExpression<?> lc && r instanceof ConstantExpression<?> rc) {
            return ConstantExpression.equalsExpression(primitives, lc, rc);
        }
        Set<Expression> leftTerms = terms(l);
        Set<Expression> rightTerms = terms(r);
        CommonTerms ct = computeCommonTerms(leftTerms, rightTerms);

        if (ct.leftTerms.isEmpty() && ct.rightTerms.isEmpty()) return new BooleanConstant(primitives, true, objectFlow);
        if (ct.leftTerms.isEmpty() || ct.rightTerms.isEmpty())
            return new BooleanConstant(primitives, false, objectFlow);

        Expression newLeft = sum(evaluationContext, ct.leftTerms);
        Expression newRight = sum(evaluationContext, ct.rightTerms);

        if (ct.common.isEmpty()) {
            return newLeft.compareTo(newRight) < 0 ? new Equals(primitives, newLeft, newRight, objectFlow) :
                    new Equals(primitives, newRight, newLeft, objectFlow);
        }
        // recurse
        return Equals.equals(evaluationContext, newLeft, newRight, objectFlow);
    }


    private static Expression sum(EvaluationContext evaluationContext, List<Expression> terms) {
        assert terms.size() > 0;
        if (terms.size() == 1) return terms.get(0);
        Collections.sort(terms);
        return terms.stream().reduce((t1, t2) -> Sum.sum(evaluationContext, t1, t2, ObjectFlow.NO_FLOW)).orElseThrow();
    }

    private static CommonTerms computeCommonTerms(Set<Expression> leftTerms, Set<Expression> rightTerms) {
        List<Expression> common = new ArrayList<>(Math.min(leftTerms.size(), rightTerms.size()));
        List<Expression> left = new ArrayList<>(leftTerms.size());
        Set<Expression> right = new HashSet<>(rightTerms); // make the copy
        for (Expression expression : leftTerms) {
            if (right.contains(expression)) {
                common.add(expression);
                right.remove(expression);
            } else {
                left.add(expression);
            }
        }
        return new CommonTerms(common, left, new ArrayList<>(right));
    }

    public record CommonTerms(List<Expression> common, List<Expression> leftTerms, List<Expression> rightTerms) {
    }

    private static Set<Expression> terms(Expression e) {
        if (e instanceof Sum sum) {
            return SetUtil.immutableUnion(terms(sum.lhs), terms(sum.rhs));
        }
        return Set.of(e);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_EQUALS;
    }

    @Override
    public ParameterizedType returnType() {
        return primitives.booleanParameterizedType;
    }
}
