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
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class Equals extends BinaryOperator {

    // public for testing
    public Equals(Identifier identifier, Primitives primitives, Expression lhs, Expression rhs) {
        super(identifier, primitives, lhs, lhs.isNumeric() ? primitives.equalsOperatorInt() : primitives.equalsOperatorObject(),
                rhs, Precedence.EQUALITY);
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression e = translationMap.translateExpression(this);
        if (e != this) return e;
        Expression tl = lhs.translate(inspectionProvider, translationMap);
        Expression tr = rhs.translate(inspectionProvider, translationMap);
        if (tl == lhs && tr == rhs) return this;
        return new Equals(identifier, primitives, tl, tr);
    }

    public static Expression equals(EvaluationResult context, Expression l, Expression r) {
        return equals(Identifier.joined("equals", List.of(l.getIdentifier(), r.getIdentifier())), context,
                l, r, true, ForwardEvaluationInfo.DEFAULT);
    }

    public static Expression equals(EvaluationResult context, Expression l, Expression r, boolean checkForNull) {
        return equals(Identifier.joined("equals", List.of(l.getIdentifier(), r.getIdentifier())), context,
                l, r, checkForNull, ForwardEvaluationInfo.DEFAULT);
    }

    public static Expression equals(Identifier identifier, EvaluationResult context, Expression l, Expression r,
                                    ForwardEvaluationInfo forwardEvaluationInfo) {
        return equals(identifier, context, l, r, true, forwardEvaluationInfo);
    }

    public static Expression equals(Identifier identifier,
                                    EvaluationResult context, Expression l, Expression r, boolean checkForNull,
                                    ForwardEvaluationInfo forwardEvaluationInfo) {
        CausesOfDelay causes = l.causesOfDelay().merge(r.causesOfDelay());
        Expression expression = internalEquals(identifier, context, l, r, checkForNull, forwardEvaluationInfo);
        return causes.isDelayed() && expression.isDone() ? DelayedExpression.forSimplification(identifier, expression.returnType(), causes) : expression;
    }

    private static Expression internalEquals(Identifier identifier,
                                             EvaluationResult context,
                                             Expression l, Expression r,
                                             boolean checkForNull,
                                             ForwardEvaluationInfo forwardEvaluationInfo) {
        Primitives primitives = context.getPrimitives();
        if (l.equals(r)) return new BooleanConstant(primitives, true);

        if (checkForNull) {
            if (l instanceof NullConstant) {
                DV dv = context.evaluationContext().isNotNull0(r, false, forwardEvaluationInfo);
                if (dv.valueIsTrue()) return new BooleanConstant(primitives, false);
                if (dv.isDelayed())
                    return DelayedExpression.forNullCheck(identifier, primitives, dv.causesOfDelay().merge(r.causesOfDelay()));
            }
            if (r instanceof NullConstant) {
                DV dv = context.evaluationContext().isNotNull0(l, false, forwardEvaluationInfo);
                if (dv.valueIsTrue()) return new BooleanConstant(primitives, false);
                if (dv.isDelayed())
                    return DelayedExpression.forNullCheck(identifier, primitives, dv.causesOfDelay().merge(l.causesOfDelay()));
            }
        }

        if (l instanceof ConstantExpression<?> lc
                && r instanceof ConstantExpression<?> rc
                && !(lc instanceof NullConstant)
                && (!(rc instanceof NullConstant))) {
            return ConstantExpression.equalsExpression(primitives, lc, rc);
        }

        if (l instanceof InlineConditional inlineConditional) {
            Expression result = tryToRewriteConstantEqualsInline(context, r, inlineConditional, forwardEvaluationInfo);
            if (result != null) return result;
        }
        if (r instanceof InlineConditional inlineConditional) {
            Expression result = tryToRewriteConstantEqualsInline(context, l, inlineConditional, forwardEvaluationInfo);
            if (result != null) return result;
        }

        Expression[] terms = Stream.concat(Sum.expandTerms(context, l, false),
                Sum.expandTerms(context, r, true)).toArray(Expression[]::new);
        Arrays.sort(terms);
        Expression[] termsOfProducts = Sum.makeProducts(context, terms);

        if (termsOfProducts.length == 0) {
            return new BooleanConstant(primitives, true);
        }
        if (termsOfProducts.length == 1) {
            if (termsOfProducts[0] instanceof Numeric) {
                return new BooleanConstant(primitives, false);
            }
            IntConstant zero = new IntConstant(primitives, 0);
            if (termsOfProducts[0] instanceof Negation neg) {
                return new Equals(identifier, primitives, zero, neg.expression);
            }
            // 0 == 3*x --> 0 == x
            if (termsOfProducts[0] instanceof Product p && p.lhs instanceof Numeric) {
                return new Equals(identifier, primitives, zero, p.rhs);
            }
            return new Equals(identifier, primitives, zero, termsOfProducts[0]);
        }
        Expression newLeft;
        Expression newRight;

        // 4 == xx; -4 == -x, ...
        if (termsOfProducts[0] instanceof Numeric numeric) {
            // -4 + -x --> -4 == x
            double d = numeric.doubleValue();
            if (d < 0 && termsOfProducts[1] instanceof Negation) {
                newLeft = termsOfProducts[0];
                newRight = wrapSum(context, termsOfProducts, true);
                // 4 + i == 0 --> -4 == i
            } else if (d > 0 && !(termsOfProducts[1] instanceof Negation)) {
                newLeft = IntConstant.intOrDouble(primitives, -d);
                newRight = wrapSum(context, termsOfProducts, false);
                // -4 + x == 0 --> 4 == x
            } else if (d < 0) {
                newLeft = IntConstant.intOrDouble(primitives, -d);
                newRight = wrapSum(context, termsOfProducts, false);
            } else {
                newLeft = termsOfProducts[0];
                newRight = wrapSum(context, termsOfProducts, true);
            }
        } else if (termsOfProducts[0] instanceof Negation neg) {
            newLeft = neg.expression;
            newRight = wrapSum(context, termsOfProducts, false);
        } else {
            newLeft = termsOfProducts[0];
            newRight = wrapSum(context, termsOfProducts, true);
        }

        // recurse
        return new Equals(identifier, primitives, newLeft, newRight);
    }

    private static Expression wrapSum(EvaluationResult evaluationContext,
                                      Expression[] termsOfProducts,
                                      boolean negate) {
        if (termsOfProducts.length == 2) {
            return negate ? Negation.negate(evaluationContext, termsOfProducts[1]) : termsOfProducts[1];
        }
        return wrapSum(evaluationContext, termsOfProducts, 1, termsOfProducts.length, negate);
    }

    private static Expression wrapSum(EvaluationResult evaluationContext,
                                      Expression[] termsOfProducts,
                                      int start, int end,
                                      boolean negate) {
        if (end - start == 2) {
            Expression s1 = termsOfProducts[start];
            Expression t1 = negate ? Negation.negate(evaluationContext, s1) : s1;
            Expression s2 = termsOfProducts[start + 1];
            Expression t2 = negate ? Negation.negate(evaluationContext, s2) : s2;
            return Sum.sum(evaluationContext, t1, t2);
        }
        Expression t1 = wrapSum(evaluationContext, termsOfProducts, start, end - 1, negate);
        Expression s2 = termsOfProducts[end - 1];
        Expression t2 = negate ? Negation.negate(evaluationContext, s2) : s2;
        return Sum.sum(evaluationContext, t1, t2);
    }

    // (a ? null: b) == null with guaranteed b != null --> !a
    // (a ? x: b) == null with guaranteed b != null --> !a&&x==null

    // GENERAL:
    // (a ? x: y) == c  ; if y != c, guaranteed, then the result is a&&x==c
    // (a ? x: y) == c  ; if x != c, guaranteed, then the result is !a&&y==c

    // see test ConditionalChecks_7; TestEqualsConstantInline
    public static Expression tryToRewriteConstantEqualsInline(EvaluationResult context,
                                                              Expression c,
                                                              InlineConditional inlineConditional,
                                                              ForwardEvaluationInfo forwardEvaluationInfo) {
        if (c instanceof InlineConditional inline2) {
            // silly check a1?b1:c1 == a1?b2:c2 === b1 == b2 && c1 == c2
            if (inline2.condition.equals(inlineConditional.condition)) {
                return And.and(context,
                        Equals.equals(context, inlineConditional.ifTrue, inline2.ifTrue),
                        Equals.equals(context, inlineConditional.ifFalse, inline2.ifFalse));
            }
            return null;
        }

        DV ifTrueGuaranteedNotEqual;
        DV ifFalseGuaranteedNotEqual;

        Expression recursively1;
        if (inlineConditional.ifTrue instanceof InlineConditional inlineTrue) {
            recursively1 = tryToRewriteConstantEqualsInline(context, c, inlineTrue, forwardEvaluationInfo);
            ifTrueGuaranteedNotEqual = recursively1 != null && recursively1.isBoolValueFalse() ? DV.TRUE_DV : DV.FALSE_DV;
        } else {
            recursively1 = null;
            if (c instanceof NullConstant) {
                ifTrueGuaranteedNotEqual = context.evaluationContext().isNotNull0(inlineConditional.ifTrue, false,
                        forwardEvaluationInfo);
            } else {
                ifTrueGuaranteedNotEqual = Equals.equals(context, inlineConditional.ifTrue, c).isBoolValueFalse() ? DV.TRUE_DV : DV.FALSE_DV;
            }
        }

        if (ifTrueGuaranteedNotEqual.valueIsTrue()) {
            Expression notCondition = Negation.negate(context, inlineConditional.condition);
            return And.and(context,
                    notCondition, Equals.equals(context, inlineConditional.ifFalse, c));
        }

        Expression recursively2;
        if (inlineConditional.ifFalse instanceof InlineConditional inlineFalse) {
            recursively2 = tryToRewriteConstantEqualsInline(context, c, inlineFalse, forwardEvaluationInfo);
            ifFalseGuaranteedNotEqual = recursively2 != null && recursively2.isBoolValueFalse() ? DV.TRUE_DV : DV.FALSE_DV;
        } else {
            recursively2 = null;
            if (c instanceof NullConstant) {
                ifFalseGuaranteedNotEqual = context.evaluationContext().isNotNull0(inlineConditional.ifFalse, false,
                        forwardEvaluationInfo);
            } else {
                ifFalseGuaranteedNotEqual = Equals.equals(context, inlineConditional.ifFalse, c).isBoolValueFalse() ? DV.TRUE_DV : DV.FALSE_DV;
            }
        }

        if (ifFalseGuaranteedNotEqual.valueIsTrue()) {
            return And.and(context,
                    inlineConditional.condition, Equals.equals(context, inlineConditional.ifTrue, c));
        }

        // we try to do something with recursive results
        if (recursively1 != null && recursively2 != null) {
            Expression notCondition = Negation.negate(context, inlineConditional.condition);
            return Or.or(context, And.and(context, inlineConditional.condition, recursively1),
                    And.and(context, notCondition, recursively2));
        }
        return null;
    }

    // (a ? null: b) != null --> !a

    // GENERAL:
    // (a ? x: y) != c  ; if y == c, guaranteed, then the result is a&&x!=c
    // (a ? x: y) != c  ; if x == c, guaranteed, then the result is !a&&y!=c

    // see test ConditionalChecks_7; TestEqualsConstantInline
    public static Expression tryToRewriteConstantEqualsInlineNegative(EvaluationResult context,
                                                                      boolean doingNullChecks,
                                                                      Expression c,
                                                                      InlineConditional inlineConditional) {
        if (c instanceof InlineConditional inline2) {
            // silly check a1?b1:c1 != a1?b2:c2 === b1 != b2 || c1 != c2
            if (inline2.condition.equals(inlineConditional.condition)) {
                return Or.or(context,
                        Negation.negate(context, Equals.equals(context, inlineConditional.ifTrue, inline2.ifTrue)),
                        Negation.negate(context, Equals.equals(context, inlineConditional.ifFalse, inline2.ifFalse)));
            }
            return null;
        }

        boolean ifTrueGuaranteedEqual;
        boolean ifFalseGuaranteedEqual;

        if (c instanceof NullConstant) {
            ifTrueGuaranteedEqual = inlineConditional.ifTrue instanceof NullConstant;
            ifFalseGuaranteedEqual = inlineConditional.ifFalse instanceof NullConstant;
        } else {
            ifTrueGuaranteedEqual = Equals.equals(context, inlineConditional.ifTrue, c).isBoolValueTrue();
            ifFalseGuaranteedEqual = Equals.equals(context, inlineConditional.ifFalse, c).isBoolValueTrue();
        }
        if (ifTrueGuaranteedEqual) {
            Expression notCondition = Negation.negate(context, inlineConditional.condition);
            return And.and(context,
                    notCondition, Negation.negate(context, Equals.equals(context, inlineConditional.ifFalse, c,
                            !doingNullChecks)));
        }
        if (ifFalseGuaranteedEqual) {
            return And.and(context, inlineConditional.condition,
                    Negation.negate(context, Equals.equals(context, inlineConditional.ifTrue, c, !doingNullChecks)));
        }
        return null;
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_EQUALS;
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        Expression l = lhs.isDelayed() ? lhs.mergeDelays(causesOfDelay) : lhs;
        Expression r = rhs.isDelayed() ? rhs.mergeDelays(causesOfDelay) : rhs;
        if (l != lhs || r != rhs) return new Equals(identifier, primitives, l, r);
        return this;
    }
}
