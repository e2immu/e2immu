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

import org.e2immu.analyser.analyser.AnnotatedAPIAnalyser;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.Message;

public class EvaluateInlineConditional {

    public static EvaluationResult conditionalValueCurrentState(EvaluationContext evaluationContext,
                                                                Expression conditionBeforeState,
                                                                Expression ifTrue,
                                                                Expression ifFalse) {
        Expression condition = evaluationContext.getConditionManager().evaluate(evaluationContext, conditionBeforeState);
        return conditionalValueConditionResolved(evaluationContext, condition, ifTrue, ifFalse);
    }

    public static EvaluationResult conditionalValueConditionResolved(EvaluationContext evaluationContext,
                                                                     Expression condition,
                                                                     Expression ifTrue,
                                                                     Expression ifFalse) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        if (condition instanceof BooleanConstant bc) {
            boolean first = bc.constant();
            builder.raiseError(Message.Label.INLINE_CONDITION_EVALUATES_TO_CONSTANT);
            return builder.setExpression(first ? ifTrue : ifFalse).build();
        }

        // not x ? a: b --> x ? b: a
        Negation negatedCondition;
        if ((negatedCondition = condition.asInstanceOf(Negation.class)) != null) {
            return conditionalValueConditionResolved(evaluationContext, negatedCondition.expression, ifFalse, ifTrue);
        }

        // isFact needs to be caught as soon as, because we're ONLY looking in the condition
        // must be checked before edgeCases (which may change the Value into an AndValue)
        // and after negation checking (so that !isFact works as well)
        if (!evaluationContext.getAnalyserContext().inAnnotatedAPIAnalysis()) {
            Expression isFact = isFact(evaluationContext, condition, ifTrue, ifFalse);
            if (isFact != null) return builder.setExpression(isFact).build();
            Expression isKnown = isKnown(evaluationContext, condition, ifTrue, ifFalse);
            if (isKnown != null) return builder.setExpression(isKnown).build();
        }

        Expression edgeCase = edgeCases(evaluationContext, condition, ifTrue, ifFalse);
        if (edgeCase != null) return builder.setExpression(edgeCase).build();

        // NOTE that x, !x cannot always be detected by the presence of Negation (see GreaterThanZero,
        // x>=10 and x<=9 for integer x
        InlineConditional ifTrueCv;
        if ((ifTrueCv = ifTrue.asInstanceOf(InlineConditional.class)) != null) {
            // x ? (x? a: b): c === x ? a : c
            if (ifTrueCv.condition.equals(condition)) {
                return conditionalValueConditionResolved(evaluationContext, condition, ifTrueCv.ifTrue, ifFalse);
            }
            // x ? (!x ? a: b): c === x ? b : c
            if (ifTrueCv.condition.equals(Negation.negate(evaluationContext, condition))) {
                return conditionalValueConditionResolved(evaluationContext, condition, ifTrueCv.ifFalse, ifFalse);
            }
            // x ? (y ? a: b): b --> x && y ? a : b
            // especially important for trailing x?(y ? z: <return variable>):<return variable>
            if (ifFalse.equals(ifTrueCv.ifFalse)) {
                return conditionalValueConditionResolved(evaluationContext,
                        And.and(evaluationContext, condition, ifTrueCv.condition), ifTrueCv.ifTrue, ifFalse);
            }
            // x ? (y ? a: b): a --> x && !y ? b: a
            if (ifFalse.equals(ifTrueCv.ifTrue)) {
                return conditionalValueConditionResolved(evaluationContext,
                        And.and(evaluationContext, condition, Negation.negate(evaluationContext, ifTrueCv.condition)),
                        ifTrueCv.ifFalse, ifFalse);
            }
        }
        // x? a: (x? b:c) === x?a:c
        InlineConditional ifFalseCv;
        if ((ifFalseCv = ifFalse.asInstanceOf(InlineConditional.class)) != null) {
            // x ? a: (x ? b:c) === x?a:c
            if (ifFalseCv.condition.equals(condition)) {
                return conditionalValueConditionResolved(evaluationContext, condition, ifTrue, ifFalseCv.ifFalse);
            }
            // x ? a: (!x ? b:c) === x?a:b
            if (ifFalseCv.condition.equals(Negation.negate(evaluationContext, condition))) {
                return conditionalValueConditionResolved(evaluationContext, condition, ifTrue, ifFalseCv.ifTrue);
            }
            // x ? a: (y ? a: b) --> x || y ? a: b
            if (ifTrue.equals(ifFalseCv.ifTrue)) {
                return conditionalValueConditionResolved(evaluationContext,
                        Or.or(evaluationContext, condition, ifFalseCv.condition), ifTrue, ifFalseCv.ifFalse);
            }
            // x ? a: (y ? b: a) --> x || !y ? a: b
            if (ifTrue.equals(ifFalseCv.ifFalse)) {
                return conditionalValueConditionResolved(evaluationContext,
                        Or.or(evaluationContext, condition, Negation.negate(evaluationContext, ifFalseCv.condition)),
                        ifTrue, ifFalseCv.ifTrue);
            }
        }

        // x&y ? (y&z ? a : b) : c --> x&y ? (z ? a : b) : c
        if (ifTrue instanceof InlineConditional ifTrueInline && ifTrueInline.condition instanceof And and) {
            Expression ifTrueCondition = removeCommonClauses(evaluationContext, condition, and);
            if (!ifTrueCondition.equals(ifTrueInline.condition)) {
                return conditionalValueConditionResolved(evaluationContext,
                        condition, new InlineConditional(Identifier.generate(),
                                evaluationContext.getAnalyserContext(), ifTrueCondition,
                                ifTrueInline.ifTrue, ifTrueInline.ifFalse), ifFalse);
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
                return conditionalValueConditionResolved(evaluationContext, condition, newOr, ifFalse);
            }
        }
        // x ? y : x||z --> x ? y: z
        // x ? y : !x||z --> x ? y: true --> !x || y
        if (ifFalse instanceof Or or) {
            if (or.expressions().contains(condition)) {
                Expression newOr = Or.or(evaluationContext,
                        or.expressions().stream().filter(e -> !e.equals(condition)).toList());
                return conditionalValueConditionResolved(evaluationContext, condition, ifTrue, newOr);
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
                return conditionalValueConditionResolved(evaluationContext, condition, newAnd, ifFalse);
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
                return conditionalValueConditionResolved(evaluationContext, condition, ifTrue, newAnd);
            }
        }

        // standardization... we swap!
        // this will result in  a != null ? a: x ==>  null == a ? x : a as the default form

        return builder.setExpression(new InlineConditional(Identifier.generate(), evaluationContext.getAnalyserContext(),
                condition, ifTrue, ifFalse)).build();
        // TODO more advanced! if a "large" part of ifTrue or ifFalse appears in condition, we should create a temp variable
    }

    private static Expression removeCommonClauses(EvaluationContext evaluationContext, Expression condition, And and) {
        return And.and(evaluationContext,
                and.getExpressions().stream().filter(e -> !inExpression(e, condition)).toArray(Expression[]::new));
    }

    private static boolean inExpression(Expression e, Expression container) {
        if (container instanceof And and) {
            return and.getExpressions().contains(e);
        }
        return container.equals(e);
    }

    private static Expression edgeCases(EvaluationContext evaluationContext,
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
        return null;
    }

    // isFact(contains(e)) will check if "contains(e)" is part of the current instance's state
    // it will bypass ConditionalValue and return ifTrue or ifFalse accordingly
    // note that we don't need to check for !isFact() because the inversion has already taken place
    private static Expression isFact(EvaluationContext evaluationContext, Expression condition, Expression ifTrue, Expression ifFalse) {
        if (condition instanceof MethodCall methodValue &&
                AnnotatedAPIAnalyser.IS_FACT_FQN.equals(methodValue.methodInfo.fullyQualifiedName)) {
            return inState(evaluationContext, methodValue.parameterExpressions.get(0)) ? ifTrue : ifFalse;
        }
        return null;
    }

    private static boolean inState(EvaluationContext evaluationContext, Expression expression) {
        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
        Expression absoluteState = evaluationContext.getConditionManager().absoluteState(evaluationContext);
        Filter.FilterResult<Expression> res = filter.filter(absoluteState, new Filter.ExactValue(filter.getDefaultRest(), expression));
        return !res.accepted().isEmpty();
    }

    // whilst isKnown is also caught at the level of MethodCall, we grab it here to avoid warnings for
    // constant evaluation
    private static Expression isKnown(EvaluationContext evaluationContext, Expression condition, Expression ifTrue, Expression ifFalse) {
        if (condition instanceof MethodCall methodValue &&
                AnnotatedAPIAnalyser.IS_KNOWN_FQN.equals(methodValue.methodInfo.fullyQualifiedName) &&
                methodValue.parameterExpressions.get(0) instanceof BooleanConstant boolValue && boolValue.constant()) {
            VariableExpression object = new VariableExpression(new This(evaluationContext.getAnalyserContext(), methodValue.methodInfo.typeInfo));
            Expression knownValue = new MethodCall(Identifier.generate(),
                    object, methodValue.methodInfo, methodValue.parameterExpressions);
            return inState(evaluationContext, knownValue) ? ifTrue : ifFalse;
        }
        return null;
    }


}
