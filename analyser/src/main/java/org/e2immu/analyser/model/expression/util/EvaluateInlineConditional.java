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
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.MethodInspection;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;

import java.util.List;

public class EvaluateInlineConditional {

    public static EvaluationResult conditionalValueCurrentState(EvaluationResult context,
                                                                Expression conditionBeforeState,
                                                                Expression ifTrue,
                                                                Expression ifFalse) {
        Expression condition = context.evaluationContext().getConditionManager()
                .evaluate(context, conditionBeforeState, false);
        return conditionalValueConditionResolved(context, condition, ifTrue, ifFalse, false, null, DV.FALSE_DV);
    }

    public static EvaluationResult conditionalValueConditionResolved(EvaluationResult evaluationContext,
                                                                     Expression condition,
                                                                     Expression ifTrue,
                                                                     Expression ifFalse,
                                                                     boolean complain,
                                                                     Variable mySelf,
                                                                     DV modifying) {
        EvaluationResult evaluationResult = compute(evaluationContext, condition, ifTrue, ifFalse, complain, mySelf, modifying);
        if (evaluationResult.value().isDone()) {
            CausesOfDelay causes = condition.causesOfDelay().merge(ifTrue.causesOfDelay()).merge(ifFalse.causesOfDelay());
            if (causes.isDelayed()) {
                Identifier identifier = Identifier.joined("inline", List.of(condition.getIdentifier(),
                        ifTrue.getIdentifier(), ifFalse.getIdentifier()));
                Expression delay = DelayedExpression.forSimplification(identifier, evaluationResult.value().returnType(),
                        evaluationResult.value(), causes);
                return new EvaluationResultImpl.Builder(evaluationContext).setExpression(delay).build();
            }
        }
        return evaluationResult;
    }

    public static EvaluationResult compute(EvaluationResult evaluationResult,
                                           Expression condition,
                                           Expression ifTrue,
                                           Expression ifFalse,
                                           boolean complain,
                                           Variable myself,
                                           DV modifying) {
        EvaluationResultImpl.Builder builder = new EvaluationResultImpl.Builder(evaluationResult);
        if (condition.isBooleanConstant()) {
            boolean first = condition.isBoolValueTrue();
            if (complain) {
                builder.raiseError(condition.getIdentifier(), Message.Label.INLINE_CONDITION_EVALUATES_TO_CONSTANT);
            }
            return builder.setExpression(first ? ifTrue : ifFalse).build();
        }

        // not x ? a: b --> x ? b: a
        Negation negatedCondition;
        if ((negatedCondition = condition.asInstanceOf(Negation.class)) != null) {
            return compute(evaluationResult, negatedCondition.expression, ifFalse, ifTrue, complain, myself, modifying);
        }

        Expression edgeCase = edgeCases(evaluationResult, condition, ifTrue, ifFalse);
        if (edgeCase != null) return builder.setExpression(edgeCase).build();

        // NOTE that x, !x cannot always be detected by the presence of Negation (see GreaterThanZero,
        // x>=10 and x<=9 for integer x
        InlineConditional ifTrueCv;
        if ((ifTrueCv = ifTrue.asInstanceOf(InlineConditional.class)) != null) {
            // x ? (x? a: b): c === x ? a : c
            if (ifTrueCv.condition.equals(condition)) {
                return compute(evaluationResult, condition, ifTrueCv.ifTrue, ifFalse, complain, myself, modifying);
            }
            // x ? (!x ? a: b): c === x ? b : c
            if (ifTrueCv.condition.equals(Negation.negate(evaluationResult, condition))) {
                return compute(evaluationResult, condition, ifTrueCv.ifFalse, ifFalse, complain, myself, modifying);
            }
            // x ? (y ? a: b): b --> x && y ? a : b
            // especially important for trailing x?(y ? z: <return variable>):<return variable>
            if (ifFalse.equals(ifTrueCv.ifFalse)) {
                return compute(evaluationResult,
                        And.and(evaluationResult, condition, ifTrueCv.condition), ifTrueCv.ifTrue, ifFalse, complain, myself, modifying);
            }
            // x ? (y ? a: b): a --> x && !y ? b: a
            if (ifFalse.equals(ifTrueCv.ifTrue)) {
                return compute(evaluationResult,
                        And.and(evaluationResult, condition, Negation.negate(evaluationResult, ifTrueCv.condition)),
                        ifTrueCv.ifFalse, ifFalse, complain, myself, modifying);
            }
        }
        // x? a: (x? b:c) === x?a:c
        InlineConditional ifFalseCv;
        if ((ifFalseCv = ifFalse.asInstanceOf(InlineConditional.class)) != null) {
            // x ? a: (x ? b:c) === x?a:c
            if (ifFalseCv.condition.equals(condition)) {
                return compute(evaluationResult, condition, ifTrue, ifFalseCv.ifFalse, complain, myself, modifying);
            }
            // x ? a: (!x ? b:c) === x?a:b
            if (ifFalseCv.condition.equals(Negation.negate(evaluationResult, condition))) {
                return compute(evaluationResult, condition, ifTrue, ifFalseCv.ifTrue, complain, myself, modifying);
            }
            // x ? a: (y ? a: b) --> x || y ? a: b
            if (ifTrue.equals(ifFalseCv.ifTrue)) {
                return compute(evaluationResult,
                        Or.or(evaluationResult, condition, ifFalseCv.condition), ifTrue, ifFalseCv.ifFalse, complain, myself, modifying);
            }
            // x ? a: (y ? b: a) --> x || !y ? a: b
            if (ifTrue.equals(ifFalseCv.ifFalse)) {
                return compute(evaluationResult,
                        Or.or(evaluationResult, condition, Negation.negate(evaluationResult, ifFalseCv.condition)),
                        ifTrue, ifFalseCv.ifTrue, complain, myself, modifying);
            }
            // 4==i ? a : 3==i ? b : c --> 3==i first, then 4==i
            if (hiddenSwitch(evaluationResult.getAnalyserContext(), condition, ifFalseCv.condition)
                    && condition.compareTo(ifFalseCv.condition) > 0) {
                if (modifying.isDelayed()) {
                    // we cannot execute this swap yet
                    InlineConditional inline = new InlineConditional(evaluationResult.getAnalyserContext(), condition, ifTrue, ifFalse);
                    Expression delayInline = DelayedExpression.forModification(inline, modifying.causesOfDelay());
                    return builder.setExpression(delayInline).build();
                }
                if (modifying.valueIsFalse()) {
                    // swap
                    EvaluationResult result = compute(evaluationResult, condition, ifTrue, ifFalseCv.ifFalse, complain, myself, modifying);
                    InlineConditional inline = new InlineConditional(evaluationResult.getAnalyserContext(),
                            ifFalseCv.condition, ifFalseCv.ifTrue, result.getExpression());
                    return builder.compose(result).setExpression(inline).build();
                }
            }
        }

        // x&y ? (y&z ? a : b) : c --> x&y ? (z ? a : b) : c
        InlineConditional ifTrueInline;
        And and1;
        if ((ifTrueInline = ifTrue.asInstanceOf(InlineConditional.class)) != null
                && (and1 = ifTrueInline.condition.asInstanceOf(And.class)) != null) {
            Expression ifTrueCondition = removeCommonClauses(evaluationResult, condition, and1);
            if (ifTrueCondition != ifTrueInline.condition) {
                return compute(evaluationResult,
                        condition, new InlineConditional(evaluationResult.getAnalyserContext(), ifTrueCondition,
                                ifTrueInline.ifTrue, ifTrueInline.ifFalse), ifFalse, complain, myself, modifying);
            }
        }


        // x ? x||y : z   -->   x ? true : z   --> x||z
        // x ? !x||y : z  -->   x ? y : z
        Or or1;
        if ((or1 = ifTrue.asInstanceOf(Or.class)) != null) {
            if (or1.expressions().contains(condition)) {
                Expression res = Or.or(evaluationResult, condition, ifFalse);
                return builder.setExpression(res).build();
            }
            Expression notCondition = Negation.negate(evaluationResult, condition);
            if (or1.expressions().contains(notCondition)) {
                Expression newOr = Or.or(evaluationResult,
                        or1.expressions().stream().filter(e -> !e.equals(notCondition)).toList());
                return compute(evaluationResult, condition, newOr, ifFalse, complain, myself, modifying);
            }
        }
        // x ? y : x||z --> x ? y: z
        // x ? y : !x||z --> x ? y: true --> !x || y
        Or or2;
        if ((or2 = ifFalse.asInstanceOf(Or.class)) != null) {
            if (or2.expressions().contains(condition)) {
                Expression newOr = Or.or(evaluationResult,
                        or2.expressions().stream().filter(e -> !e.equals(condition)).toList());
                return compute(evaluationResult, condition, ifTrue, newOr, complain, myself, modifying);
            }
            Expression notCondition = Negation.negate(evaluationResult, condition);
            if (or2.expressions().contains(notCondition)) {
                Expression res = Or.or(evaluationResult, notCondition, ifTrue);
                return builder.setExpression(res).build();
            }
        }
        // x ? x&&y : z --> x ? y : z
        // x ? !x&&y : z --> x ? false : z --> !x && z
        And and2;
        if ((and2 = ifTrue.asInstanceOf(And.class)) != null) {
            if (and2.getExpressions().contains(condition)) {
                Expression newAnd = And.and(evaluationResult,
                        and2.getExpressions().stream().filter(e -> !e.equals(condition)).toArray(Expression[]::new));
                return compute(evaluationResult, condition, newAnd, ifFalse, complain, myself, modifying);
            }
            Expression notCondition = Negation.negate(evaluationResult, condition);
            if (and2.getExpressions().contains(notCondition)) {
                Expression res = And.and(evaluationResult, notCondition, ifFalse);
                return builder.setExpression(res).build();
            }
        }
        // x ? y : !x&&z => x ? y : z
        // x ? y : x&&z --> x ? y : false --> x && y
        And and3;
        if ((and3 = ifFalse.asInstanceOf(And.class)) != null) {
            if (and3.getExpressions().contains(condition)) {
                Expression res = And.and(evaluationResult, condition, ifTrue);
                return builder.setExpression(res).build();
            }
            Expression notCondition = Negation.negate(evaluationResult, condition);
            if (and3.getExpressions().contains(notCondition)) {
                Expression newAnd = And.and(evaluationResult,
                        and3.getExpressions().stream().filter(e -> !e.equals(notCondition)).toArray(Expression[]::new));
                return compute(evaluationResult, condition, ifTrue, newAnd, complain, myself, modifying);
            }
        }

        // myself == x ? x : y --> y
        Equals equals;
        if ((equals = condition.asInstanceOf(Equals.class)) != null && myself != null) {
            VariableExpression vel;
            VariableExpression ver;
            if ((vel = equals.lhs.asInstanceOf(VariableExpression.class)) != null
                    && vel.variable().equals(myself) && ifTrue.equals(equals.rhs)
                    || (ver = equals.rhs.asInstanceOf(VariableExpression.class)) != null
                    && ver.variable().equals(myself) && ifTrue.equals(equals.lhs)) {
                return builder.setExpression(ifFalse).build();
            }
        }

        GreaterThanZero ge0;
        Sum sum;
        if (evaluationResult.getAnalyserContext().getConfiguration().analyserConfiguration().normalizeMore()
                && (ge0 = condition.asInstanceOf(GreaterThanZero.class)) != null
                && (sum = ge0.expression().asInstanceOf(Sum.class)) != null) {
            /*
            if lhs is negative, and rhs is positive, keep: a<=b?t:f === -a+b>0?t:f
            if lhs is positive, and rhs is negative, swap: b>a?t:f  === a-b>0?t:f  === a<=b?f:t
             */
            boolean lhsNegative = sum.lhs.isNegatedOrNumericNegative();
            boolean rhsNegative = sum.rhs.isNegatedOrNumericNegative();
            if (!lhsNegative && rhsNegative) {
                Expression newCondition = Negation.negate(evaluationResult, condition);
                InlineConditional inline = new InlineConditional(evaluationResult.getAnalyserContext(),
                        newCondition, ifFalse, ifTrue);
                return builder.setExpression(inline).build();
            }
        }

        // standardization... we swap!
        // this will result in  a != null ? a: x ==>  null == a ? x : a as the default form

        Identifier id = Identifier.joined("inline conditional",
                List.of(condition.getIdentifier(), ifTrue.getIdentifier(), ifFalse.getIdentifier()));
        return builder.setExpression(new InlineConditional(id, evaluationResult.getAnalyserContext(),
                condition, ifTrue, ifFalse)).build();
        // TODO more advanced! if a "large" part of ifTrue or ifFalse appears in condition, we should create a temp variable
    }

    private static Expression removeCommonClauses(EvaluationResult evaluationContext, Expression condition, And and) {
        Expression[] filtered = and.getExpressions().stream().filter(e -> !inExpression(e, condition)).toArray(Expression[]::new);
        if (filtered.length == and.getExpressions().size()) return and;
        return And.and(evaluationContext, filtered);
    }

    private static boolean inExpression(Expression e, Expression container) {
        And and;
        if ((and = container.asInstanceOf(And.class)) != null) {
            return and.getExpressions().contains(e);
        }
        return container.equals(e);
    }

    private static boolean hiddenSwitch(InspectionProvider inspectionProvider, Expression c1, Expression c2) {
        Equals eq1;
        Equals eq2;
        if ((eq1 = c1.asInstanceOf(Equals.class)) != null
                && (eq2 = c2.asInstanceOf(Equals.class)) != null
                && eq1.lhs.isConstant() && eq2.lhs.isConstant() && !eq1.lhs.equals(eq2.lhs)) {
            return eq1.rhs.equals(eq2.rhs);
        }
        MethodCall mc1;
        MethodCall mc2;
        if ((mc1 = c1.asInstanceOf(MethodCall.class)) != null && (mc2 = c2.asInstanceOf(MethodCall.class)) != null) {
            MethodInspection mi1 = inspectionProvider.getMethodInspection(mc1.methodInfo);
            MethodInspection mi2 = inspectionProvider.getMethodInspection(mc2.methodInfo);
            if (mi1.isEquals() && mi2.isEquals()) {
                {
                    if (mc1.object.equals(mc2.object)) {
                        // x.equals(...), x.equals(...)
                        ConstantExpression<?> ce1 = mc1.parameterExpressions.get(0).asInstanceOf(ConstantExpression.class);
                        ConstantExpression<?> ce2 = mc2.parameterExpressions.get(0).asInstanceOf(ConstantExpression.class);
                        return ce1 != null && ce2 != null && !ce1.equals(ce2);
                    }
                }
                {
                    ConstantExpression<?> ce1 = mc1.object.asInstanceOf(ConstantExpression.class);
                    ConstantExpression<?> ce2 = mc2.object.asInstanceOf(ConstantExpression.class);
                    if (ce1 != null && ce2 != null && !ce1.equals(ce2)) {
                        // "a".equals(...), "b".equals(...)
                        Expression p01 = mc1.parameterExpressions.get(0);
                        Expression p02 = mc2.parameterExpressions.get(0);
                        return p01.equals(p02);
                    }
                }
            }
        }
        return false;
    }

    private static Expression edgeCases(EvaluationResult evaluationContext,
                                        Expression condition, Expression ifTrue, Expression ifFalse) {
        if (ifTrue.isEmpty() || ifFalse.isEmpty()) {
            /*
             The inline conditional system is used to construct inline conditionals such as x?<no return value>:false
             in the context of constructing a correct return value (e2immu, but also jfocus).
             The edge cases are not applicable in that specific situation.
             */
            return null;
        }
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
        if (ifTrue.isBooleanConstant()) {
            if (ifTrue.isBoolValueTrue()) {
                return Or.or(evaluationContext, condition, ifFalse);
            }
            return And.and(evaluationContext, Negation.negate(evaluationContext, condition), ifFalse);
        }
        if (ifFalse.isBooleanConstant()) {
            if (ifFalse.isBoolValueTrue()) {
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
        Equals equals;
        if ((equals = condition.asInstanceOf(Equals.class)) != null
                && ifTrue.equals(equals.lhs) && ifFalse.equals(equals.rhs)) {
            return ifFalse;
        }
        // a == b ? b : a --> a
        Equals equals2;
        if ((equals2 = condition.asInstanceOf(Equals.class)) != null
                && ifTrue.equals(equals2.rhs) && ifFalse.equals(equals2.lhs)) {
            return ifFalse;
        }
        return null;
    }
}
