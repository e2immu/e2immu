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
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.*;

public class Equals extends BinaryOperator {

    // public for testing
    public Equals(Identifier identifier, Primitives primitives, Expression lhs, Expression rhs) {
        super(identifier, primitives, lhs, lhs.isNumeric() ? primitives.equalsOperatorInt() : primitives.equalsOperatorObject(),
                rhs, Precedence.EQUALITY);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new Equals(identifier, primitives, translationMap.translateExpression(lhs),
                translationMap.translateExpression(rhs));
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setExpression(Equals.equals(evaluationContext, reLhs.value(), reRhs.value())).build();
    }

    public static Expression equals(EvaluationContext evaluationContext, Expression l, Expression r) {
        return equals(Identifier.generate(), evaluationContext, l, r, true);
    }

    public static Expression equals(Identifier identifier, EvaluationContext evaluationContext, Expression l, Expression r) {
        return equals(identifier, evaluationContext, l, r, true);
    }

    public static Expression equals(Identifier identifier,
                                    EvaluationContext evaluationContext, Expression l, Expression r, boolean checkForNull) {
        Primitives primitives = evaluationContext.getPrimitives();
        if (l.equals(r)) return new BooleanConstant(primitives, true);

        //if (l.isUnknown() || r.isUnknown()) throw new UnsupportedOperationException();

        if (checkForNull) {
            if (l instanceof NullConstant && evaluationContext.isNotNull0(r, false) ||
                    r instanceof NullConstant && evaluationContext.isNotNull0(l, false))
                return new BooleanConstant(primitives, false);
        }

        if (l instanceof ConstantExpression<?> lc
                && r instanceof ConstantExpression<?> rc
                && !(lc instanceof NullConstant)
                && (!(rc instanceof NullConstant))) {
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

        if (ct.leftTerms.isEmpty() && ct.rightTerms.isEmpty()) return new BooleanConstant(primitives, true);
        Expression newLeft = sum(evaluationContext, ct.leftTerms);
        Expression newRight = sum(evaluationContext, ct.rightTerms);

        if (ct.common.isEmpty()) {
            return newLeft.compareTo(newRight) < 0 ? new Equals(identifier, primitives, newLeft, newRight) :
                    new Equals(identifier, primitives, newRight, newLeft);
        }
        // recurse
        return Equals.equals(identifier, evaluationContext, newLeft, newRight);
    }

    // (a ? null: b) == null with guaranteed b != null --> !a
    // (a ? x: b) == null with guaranteed b != null --> !a&&x==null

    // GENERAL:
    // (a ? x: y) == c  ; if y != c, guaranteed, then the result is a&&x==c
    // (a ? x: y) == c  ; if x != c, guaranteed, then the result is !a&&y==c

    // see test ConditionalChecks_7; TestEqualsConstantInline
    public static Expression tryToRewriteConstantEqualsInline(EvaluationContext evaluationContext,
                                                              Expression c,
                                                              InlineConditional inlineConditional) {
        if (c instanceof InlineConditional inline2) {
            // silly check a1?b1:c1 == a1?b2:c2 === b1 == b2 && c1 == c2
            if (inline2.condition.equals(inlineConditional.condition)) {
                return And.and(evaluationContext,
                        Equals.equals(evaluationContext, inlineConditional.ifTrue, inline2.ifTrue),
                        Equals.equals(evaluationContext, inlineConditional.ifFalse, inline2.ifFalse));
            }
            return null;
        }

        boolean ifTrueGuaranteedNotEqual;
        boolean ifFalseGuaranteedNotEqual;

        Expression recursively1;
        if (inlineConditional.ifTrue instanceof InlineConditional inlineTrue) {
            recursively1 = tryToRewriteConstantEqualsInline(evaluationContext, c, inlineTrue);
            ifTrueGuaranteedNotEqual = recursively1 != null && recursively1.isBoolValueFalse();
        } else {
            recursively1 = null;
            if (c instanceof NullConstant) {
                ifTrueGuaranteedNotEqual = evaluationContext.isNotNull0(inlineConditional.ifTrue, false);
            } else {
                ifTrueGuaranteedNotEqual = Equals.equals(evaluationContext, inlineConditional.ifTrue, c).isBoolValueFalse();
            }
        }

        if (ifTrueGuaranteedNotEqual) {
            Expression notCondition = Negation.negate(evaluationContext, inlineConditional.condition);
            return And.and(evaluationContext,
                    notCondition, Equals.equals(evaluationContext, inlineConditional.ifFalse, c));
        }

        Expression recursively2;
        if (inlineConditional.ifFalse instanceof InlineConditional inlineFalse) {
            recursively2 = tryToRewriteConstantEqualsInline(evaluationContext, c, inlineFalse);
            ifFalseGuaranteedNotEqual = recursively2 != null && recursively2.isBoolValueFalse();
        } else {
            recursively2 = null;
            if (c instanceof NullConstant) {
                ifFalseGuaranteedNotEqual = evaluationContext.isNotNull0(inlineConditional.ifFalse, false);
            } else {
                ifFalseGuaranteedNotEqual = Equals.equals(evaluationContext, inlineConditional.ifFalse, c).isBoolValueFalse();
            }
        }

        if (ifFalseGuaranteedNotEqual) {
            return And.and(evaluationContext,
                    inlineConditional.condition, Equals.equals(evaluationContext, inlineConditional.ifTrue, c));
        }

        // we try to do something with recursive results
        if (recursively1 != null && recursively2 != null) {
            Expression notCondition = Negation.negate(evaluationContext, inlineConditional.condition);
            return Or.or(evaluationContext, And.and(evaluationContext, inlineConditional.condition, recursively1),
                    And.and(evaluationContext, notCondition, recursively2));
        }
        return null;
    }

    // (a ? null: b) != null --> !a

    // GENERAL:
    // (a ? x: y) != c  ; if y == c, guaranteed, then the result is a&&x!=c
    // (a ? x: y) != c  ; if x == c, guaranteed, then the result is !a&&y!=c

    // see test ConditionalChecks_7; TestEqualsConstantInline
    public static Expression tryToRewriteConstantEqualsInlineNegative(EvaluationContext evaluationContext,
                                                                      Expression c,
                                                                      InlineConditional inlineConditional) {
        if (c instanceof InlineConditional inline2) {
            // silly check a1?b1:c1 != a1?b2:c2 === b1 != b2 || c1 != c2
            if (inline2.condition.equals(inlineConditional.condition)) {
                return Or.or(evaluationContext,
                        Negation.negate(evaluationContext, Equals.equals(evaluationContext, inlineConditional.ifTrue, inline2.ifTrue)),
                        Negation.negate(evaluationContext, Equals.equals(evaluationContext, inlineConditional.ifFalse, inline2.ifFalse)));
            }
            return null;
        }

        boolean ifTrueGuaranteedEqual;
        boolean ifFalseGuaranteedEqual;

        if (c instanceof NullConstant) {
            ifTrueGuaranteedEqual = inlineConditional.ifTrue instanceof NullConstant;
            ifFalseGuaranteedEqual = inlineConditional.ifFalse instanceof NullConstant;
        } else {
            ifTrueGuaranteedEqual = Equals.equals(evaluationContext, inlineConditional.ifTrue, c).isBoolValueTrue();
            ifFalseGuaranteedEqual = Equals.equals(evaluationContext, inlineConditional.ifFalse, c).isBoolValueTrue();
        }
        if (ifTrueGuaranteedEqual) {
            Expression notCondition = Negation.negate(evaluationContext, inlineConditional.condition);
            return And.and(evaluationContext,
                    notCondition, Negation.negate(evaluationContext, Equals.equals(evaluationContext, inlineConditional.ifFalse, c)));
        }
        if (ifFalseGuaranteedEqual) {
            return And.and(evaluationContext, inlineConditional.condition,
                    Negation.negate(evaluationContext, Equals.equals(evaluationContext, inlineConditional.ifTrue, c)));
        }
        return null;
    }

    public static Expression recursiveTryToRewriteConstantEqualsInline(EvaluationContext evaluationContext,
                                                                       Expression c,
                                                                       InlineConditional inlineConditional) {

        Expression recursively1;
        if (inlineConditional.ifTrue instanceof InlineConditional inlineTrue) {
            recursively1 = recursiveTryToRewriteConstantEqualsInline(evaluationContext, c, inlineTrue);
        } else recursively1 = null;

        Expression recursively2;
        if (inlineConditional.ifFalse instanceof InlineConditional inlineFalse) {
            recursively2 = recursiveTryToRewriteConstantEqualsInline(evaluationContext, c, inlineFalse);
        } else recursively2 = null;

        Expression notCondition = Negation.negate(evaluationContext, inlineConditional.condition);

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
            if (recursively2 != null) return Or.or(evaluationContext, inlineConditional.condition, recursively2);
            if (!equalsToIfFalse) return inlineConditional.condition;
        }
        if (equalsToIfFalse) {
            if (recursively1 != null) return Or.or(evaluationContext, notCondition, recursively1);
            if (!equalsToIfTrue) return notCondition;
            // FIXME here it goes wrong: !notNull does not mean: always null
        }
        List<Expression> ors = new ArrayList<>();
        if (recursively1 != null) {
            ors.add(And.and(evaluationContext, inlineConditional.condition, recursively1));
        }
        if (recursively2 != null) {
            ors.add(And.and(evaluationContext, notCondition, recursively2));
        }
        if (!ors.isEmpty()) {
            return Or.or(evaluationContext, ors);
        }
        return null;
    }


    private static Expression sum(EvaluationContext evaluationContext, List<Expression> terms) {
        if (terms.size() == 0) return new IntConstant(evaluationContext.getPrimitives(), 0);
        if (terms.size() == 1) return terms.get(0);
        Collections.sort(terms);
        return terms.stream().reduce((t1, t2) -> Sum.sum(evaluationContext, t1, t2)).orElseThrow();
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
}
