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
import org.e2immu.analyser.model.TranslationMap;
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
    public Expression translate(TranslationMap translationMap) {
        return new Equals(primitives, translationMap.translateExpression(lhs), translationMap.translateExpression(rhs), objectFlow);
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setExpression(Equals.equals(evaluationContext, reLhs.value(), reRhs.value(), objectFlow)).build();
    }

    public static Expression equals(EvaluationContext evaluationContext, Expression l, Expression r, ObjectFlow objectFlow) {
        return equals(evaluationContext, l, r, objectFlow, true);
    }

    public static Expression equals(EvaluationContext evaluationContext, Expression l, Expression r, ObjectFlow objectFlow,
                                    boolean checkForNull) {
        Primitives primitives = evaluationContext.getPrimitives();
        if (l.equals(r)) return new BooleanConstant(primitives, true, objectFlow);

        //if (l.isUnknown() || r.isUnknown()) throw new UnsupportedOperationException();

        if (checkForNull) {
            if (l instanceof NullConstant && evaluationContext.isNotNull0(r, false) ||
                    r instanceof NullConstant && evaluationContext.isNotNull0(l, false))
                return new BooleanConstant(primitives, false, objectFlow);
        }

        if (l instanceof ConstantExpression<?> lc && r instanceof ConstantExpression<?> rc) {
            return ConstantExpression.equalsExpression(primitives, lc, rc);
        }

        if (l instanceof InlineConditional inlineConditional) {
            Expression result = tryToRewriteConstantEqualsInline(evaluationContext, r, inlineConditional);
            if (result != null) return result;
        }
        if (r instanceof InlineConditional inlineConditional) {
            Expression result = tryToRewriteConstantEqualsInline(evaluationContext, l, inlineConditional);
            if (result != null) return result;
        }

        Set<Expression> leftTerms = terms(l);
        Set<Expression> rightTerms = terms(r);
        CommonTerms ct = computeCommonTerms(leftTerms, rightTerms);

        if (ct.leftTerms.isEmpty() && ct.rightTerms.isEmpty()) return new BooleanConstant(primitives, true, objectFlow);
        Expression newLeft = sum(evaluationContext, ct.leftTerms);
        Expression newRight = sum(evaluationContext, ct.rightTerms);

        if (ct.common.isEmpty()) {
            return newLeft.compareTo(newRight) < 0 ? new Equals(primitives, newLeft, newRight, objectFlow) :
                    new Equals(primitives, newRight, newLeft, objectFlow);
        }
        // recurse
        return Equals.equals(evaluationContext, newLeft, newRight, objectFlow);
    }

    // null == a ? null: b with guaranteed b != null --> a
    // see test ConditionalChecks_7
    private static Expression tryToRewriteConstantEqualsInline(EvaluationContext evaluationContext,
                                                               Expression c,
                                                               InlineConditional inlineConditional) {
        if (c instanceof InlineConditional inline2) {
            // silly check a1?b1:c1 == a1?b2:c2 === b1 == b2 && c1 == c2
            if (inline2.condition.equals(inlineConditional.condition)) {
                return new And(evaluationContext.getPrimitives()).append(evaluationContext,
                        Equals.equals(evaluationContext, inlineConditional.ifTrue, inline2.ifTrue, ObjectFlow.NO_FLOW),
                        Equals.equals(evaluationContext, inlineConditional.ifFalse, inline2.ifFalse, ObjectFlow.NO_FLOW));
            }
            return null;
        }
        Expression recursively1;
        if (inlineConditional.ifTrue instanceof InlineConditional inlineTrue) {
            recursively1 = tryToRewriteConstantEqualsInline(evaluationContext, c, inlineTrue);
        } else recursively1 = null;

        Expression recursively2;
        if (inlineConditional.ifFalse instanceof InlineConditional inlineFalse) {
            recursively2 = tryToRewriteConstantEqualsInline(evaluationContext, c, inlineFalse);
        } else recursively2 = null;

        Expression notC = Negation.negate(evaluationContext, inlineConditional.condition);

        boolean equalsToIfTrue;
        boolean equalsToIfFalse;

        if (c instanceof NullConstant) {
            // if recursivelyX is not null, isNotNull0 will always be true
            equalsToIfTrue = recursively1 == null && !evaluationContext.isNotNull0(inlineConditional.ifTrue, false);
            equalsToIfFalse = recursively2 == null && !evaluationContext.isNotNull0(inlineConditional.ifFalse, false);
        } else {
            equalsToIfTrue = c.equals(inlineConditional.ifTrue);
            equalsToIfFalse = c.equals(inlineConditional.ifFalse);
        }
        if (equalsToIfTrue) {
            if (recursively2 != null)
                return new Or(evaluationContext.getPrimitives()).append(evaluationContext, inlineConditional.condition, recursively2);
            if (!equalsToIfFalse) return inlineConditional.condition;
        }
        if (equalsToIfFalse) {
            if (recursively1 != null)
                return new Or(evaluationContext.getPrimitives()).append(evaluationContext, notC, recursively1);
            if (!equalsToIfTrue) return notC;
        }
        List<Expression> ors = new ArrayList<>();
        if (recursively1 != null) {
            ors.add(new And(evaluationContext.getPrimitives()).append(evaluationContext, inlineConditional.condition, recursively1));
        }
        if (recursively2 != null) {
            ors.add(new And(evaluationContext.getPrimitives()).append(evaluationContext, notC, recursively2));
        }
        if (!ors.isEmpty()) {
            return new Or(evaluationContext.getPrimitives()).append(evaluationContext, ors.toArray(Expression[]::new));
        }
        return null;
    }


    private static Expression sum(EvaluationContext evaluationContext, List<Expression> terms) {
        if (terms.size() == 0) return new IntConstant(evaluationContext.getPrimitives(), 0);
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
