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

package org.e2immu.analyser.model.expression.util;

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Message;

import java.util.List;

public class EvaluateInlineConditional {

    public static EvaluationResult conditionalValueCurrentState(EvaluationResult context,
                                                                Expression conditionBeforeState,
                                                                Expression ifTrue,
                                                                Expression ifFalse) {
        Expression condition = context.evaluationContext().getConditionManager()
                .evaluate(context, conditionBeforeState, false);
        return conditionalValueConditionResolved(context, condition, ifTrue, ifFalse, false, null);
    }

    public static EvaluationResult conditionalValueConditionResolved(EvaluationResult evaluationContext,
                                                                     Expression condition,
                                                                     Expression ifTrue,
                                                                     Expression ifFalse,
                                                                     boolean complain,
                                                                     Variable mySelf) {
        EvaluationResult evaluationResult = compute(evaluationContext, condition, ifTrue, ifFalse, complain, mySelf);
        if (evaluationResult.value().isDone()) {
            CausesOfDelay causes = condition.causesOfDelay().merge(ifTrue.causesOfDelay()).merge(ifFalse.causesOfDelay());
            if (causes.isDelayed()) {
                Identifier identifier = Identifier.joined("inline", List.of(condition.getIdentifier(),
                        ifTrue.getIdentifier(), ifFalse.getIdentifier()));
                Expression delay = DelayedExpression.forSimplification(identifier, evaluationResult.value().returnType(),
                        evaluationResult.value(), causes);
                return new EvaluationResult.Builder(evaluationContext).setExpression(delay).build();
            }
        }
        return evaluationResult;
    }

    public static EvaluationResult compute(EvaluationResult evaluationContext,
                                           Expression condition,
                                           Expression ifTrue,
                                           Expression ifFalse,
                                           boolean complain,
                                           Variable myself) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        if (condition instanceof BooleanConstant bc) {
            boolean first = bc.constant();
            if (complain) {
                builder.raiseError(condition.getIdentifier(), Message.Label.INLINE_CONDITION_EVALUATES_TO_CONSTANT);
            }
            return builder.setExpression(first ? ifTrue : ifFalse).build();
        }

        // not x ? a: b --> x ? b: a
        Negation negatedCondition;
        if ((negatedCondition = condition.asInstanceOf(Negation.class)) != null) {
            return compute(evaluationContext, negatedCondition.expression, ifFalse, ifTrue, complain, myself);
        }

        Expression edgeCase = edgeCases(evaluationContext, condition, ifTrue, ifFalse);
        if (edgeCase != null) return builder.setExpression(edgeCase).build();

        // NOTE that x, !x cannot always be detected by the presence of Negation (see GreaterThanZero,
        // x>=10 and x<=9 for integer x
        InlineConditional ifTrueCv;
        if ((ifTrueCv = ifTrue.asInstanceOf(InlineConditional.class)) != null) {
            // x ? (x? a: b): c === x ? a : c
            if (ifTrueCv.condition.equals(condition)) {
                return compute(evaluationContext, condition, ifTrueCv.ifTrue, ifFalse, complain, myself);
            }
            // x ? (!x ? a: b): c === x ? b : c
            if (ifTrueCv.condition.equals(Negation.negate(evaluationContext, condition))) {
                return compute(evaluationContext, condition, ifTrueCv.ifFalse, ifFalse, complain, myself);
            }
            // x ? (y ? a: b): b --> x && y ? a : b
            // especially important for trailing x?(y ? z: <return variable>):<return variable>
            if (ifFalse.equals(ifTrueCv.ifFalse)) {
                return compute(evaluationContext,
                        And.and(evaluationContext, condition, ifTrueCv.condition), ifTrueCv.ifTrue, ifFalse, complain, myself);
            }
            // x ? (y ? a: b): a --> x && !y ? b: a
            if (ifFalse.equals(ifTrueCv.ifTrue)) {
                return compute(evaluationContext,
                        And.and(evaluationContext, condition, Negation.negate(evaluationContext, ifTrueCv.condition)),
                        ifTrueCv.ifFalse, ifFalse, complain, myself);
            }
        }
        // x? a: (x? b:c) === x?a:c
        InlineConditional ifFalseCv;
        if ((ifFalseCv = ifFalse.asInstanceOf(InlineConditional.class)) != null) {
            // x ? a: (x ? b:c) === x?a:c
            if (ifFalseCv.condition.equals(condition)) {
                return compute(evaluationContext, condition, ifTrue, ifFalseCv.ifFalse, complain, myself);
            }
            // x ? a: (!x ? b:c) === x?a:b
            if (ifFalseCv.condition.equals(Negation.negate(evaluationContext, condition))) {
                return compute(evaluationContext, condition, ifTrue, ifFalseCv.ifTrue, complain, myself);
            }
            // x ? a: (y ? a: b) --> x || y ? a: b
            if (ifTrue.equals(ifFalseCv.ifTrue)) {
                return compute(evaluationContext,
                        Or.or(evaluationContext, condition, ifFalseCv.condition), ifTrue, ifFalseCv.ifFalse, complain, myself);
            }
            // x ? a: (y ? b: a) --> x || !y ? a: b
            if (ifTrue.equals(ifFalseCv.ifFalse)) {
                return compute(evaluationContext,
                        Or.or(evaluationContext, condition, Negation.negate(evaluationContext, ifFalseCv.condition)),
                        ifTrue, ifFalseCv.ifTrue, complain, myself);
            }
        }

        // x&y ? (y&z ? a : b) : c --> x&y ? (z ? a : b) : c
        if (ifTrue instanceof InlineConditional ifTrueInline && ifTrueInline.condition instanceof And and) {
            Expression ifTrueCondition = removeCommonClauses(evaluationContext, condition, and);
            if (ifTrueCondition != ifTrueInline.condition) {
                return compute(evaluationContext,
                        condition, new InlineConditional(evaluationContext.getAnalyserContext(), ifTrueCondition,
                                ifTrueInline.ifTrue, ifTrueInline.ifFalse), ifFalse, complain, myself);
            }
        }


        // x ? x||y : z   -->   x ? true : z   --> x||z
        // x ? !x||y : z  -->   x ? y : z
        if (ifTrue instanceof Or or) {
            if (or.expressions().contains(condition)) {
                Expression res = Or.or(evaluationContext, condition, ifFalse);
                return builder.setExpression(res).build();
            }
            Expression notCondition = Negation.negate(evaluationContext, condition);
            if (or.expressions().contains(notCondition)) {
                Expression newOr = Or.or(evaluationContext,
                        or.expressions().stream().filter(e -> !e.equals(notCondition)).toList());
                return compute(evaluationContext, condition, newOr, ifFalse, complain, myself);
            }
        }
        // x ? y : x||z --> x ? y: z
        // x ? y : !x||z --> x ? y: true --> !x || y
        if (ifFalse instanceof Or or) {
            if (or.expressions().contains(condition)) {
                Expression newOr = Or.or(evaluationContext,
                        or.expressions().stream().filter(e -> !e.equals(condition)).toList());
                return compute(evaluationContext, condition, ifTrue, newOr, complain, myself);
            }
            Expression notCondition = Negation.negate(evaluationContext, condition);
            if (or.expressions().contains(notCondition)) {
                Expression res = Or.or(evaluationContext, notCondition, ifTrue);
                return builder.setExpression(res).build();
            }
        }
        // x ? x&&y : z --> x ? y : z
        // x ? !x&&y : z --> x ? false : z --> !x && z
        if (ifTrue instanceof And and) {
            if (and.getExpressions().contains(condition)) {
                Expression newAnd = And.and(evaluationContext,
                        and.getExpressions().stream().filter(e -> !e.equals(condition)).toArray(Expression[]::new));
                return compute(evaluationContext, condition, newAnd, ifFalse, complain, myself);
            }
            Expression notCondition = Negation.negate(evaluationContext, condition);
            if (and.getExpressions().contains(notCondition)) {
                Expression res = And.and(evaluationContext, notCondition, ifFalse);
                return builder.setExpression(res).build();
            }
        }
        // x ? y : !x&&z => x ? y : z
        // x ? y : x&&z --> x ? y : false --> x && y
        if (ifFalse instanceof And and) {
            if (and.getExpressions().contains(condition)) {
                Expression res = And.and(evaluationContext, condition, ifTrue);
                return builder.setExpression(res).build();
            }
            Expression notCondition = Negation.negate(evaluationContext, condition);
            if (and.getExpressions().contains(notCondition)) {
                Expression newAnd = And.and(evaluationContext,
                        and.getExpressions().stream().filter(e -> !e.equals(notCondition)).toArray(Expression[]::new));
                return compute(evaluationContext, condition, ifTrue, newAnd, complain, myself);
            }
        }

        // myself == x ? x : y --> y
        if (condition instanceof Equals equals && myself != null) {
            if (equals.lhs instanceof VariableExpression vel && vel.variable().equals(myself) && ifTrue.equals(equals.rhs)
                    || equals.rhs instanceof VariableExpression ver && ver.variable().equals(myself) && ifTrue.equals(equals.lhs)) {
                return builder.setExpression(ifFalse).build();
            }
        }

        // standardization... we swap!
        // this will result in  a != null ? a: x ==>  null == a ? x : a as the default form

        Identifier id = Identifier.joined("inline conditional",
                List.of(condition.getIdentifier(), ifTrue.getIdentifier(), ifFalse.getIdentifier()));
        return builder.setExpression(new InlineConditional(id, evaluationContext.getAnalyserContext(),
                condition, ifTrue, ifFalse)).build();
        // TODO more advanced! if a "large" part of ifTrue or ifFalse appears in condition, we should create a temp variable
    }

    private static Expression removeCommonClauses(EvaluationResult evaluationContext, Expression condition, And and) {
        Expression[] filtered = and.getExpressions().stream().filter(e -> !inExpression(e, condition)).toArray(Expression[]::new);
        if (filtered.length == and.getExpressions().size()) return and;
        return And.and(evaluationContext, filtered);
    }

    private static boolean inExpression(Expression e, Expression container) {
        if (container instanceof And and) {
            return and.getExpressions().contains(e);
        }
        return container.equals(e);
    }

    private static Expression edgeCases(EvaluationResult evaluationContext,
                                        Expression condition, Expression ifTrue, Expression ifFalse) {
        // x ? a : a == a
        if (ifTrue.equals(ifFalse)) return ifTrue;
        // a ? a : !a == a == !a ? !a : a
        if (condition.equals(ifTrue) && condition.equals(Negation.negate(evaluationContext, ifFalse))) {
            return new BooleanConstant(evaluationContext.getPrimitives(), true);
        }
        // !a ? a : !a == !a == a ? !a : a --> will not happen, as we've already swapped

        // a ? true: b  --> a || b
        // a ? b : true --> !a || b
        // a ? b : false --> a && b
        // a ? false: b --> !a && b
        if (ifTrue instanceof BooleanConstant ifTrueBool) {
            if (ifTrueBool.constant()) {
                return Or.or(evaluationContext, condition, ifFalse);
            }
            return And.and(evaluationContext, Negation.negate(evaluationContext, condition), ifFalse);
        }
        if (ifFalse instanceof BooleanConstant ifFalseBool) {
            if (ifFalseBool.constant()) {
                return Or.or(evaluationContext, Negation.negate(evaluationContext, condition), ifTrue);
            }
            return And.and(evaluationContext, condition, ifTrue);
        }

        // x ? a : a --> a, but only if x is not modifying TODO needs implementing!!
        if (ifTrue.equals(ifFalse)) {// && evaluationContext.getProperty(condition, VariableProperty.MODIFIED) == Level.FALSE) {
            return ifTrue;
        }

        LhsRhs eq = LhsRhs.equalsMethodCall(condition);
        // a.equals(b) ? a : b ---> b
        if (eq != null && ifTrue.equals(eq.lhs()) && ifFalse.equals(eq.rhs())) {
            return ifFalse;
        }

        // a == b ? a : b ---> b
        if (condition instanceof Equals equals && ifTrue.equals(equals.lhs) && ifFalse.equals(equals.rhs)) {
            return ifFalse;
        }
        // a == b ? b : a --> a
        if (condition instanceof Equals equals && ifTrue.equals(equals.rhs) && ifFalse.equals(equals.lhs)) {
            return ifFalse;
        }
        return null;
    }
}
