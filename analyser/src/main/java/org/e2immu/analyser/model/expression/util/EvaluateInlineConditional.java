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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.AnnotatedAPIAnalyser;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;

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

        // NOTE that x, !x cannot always be detected by the presence of Negation (see GreaterThanZero,
        // x>=10 and x<=9 for integer x
        InlineConditional secondCv;
        if ((secondCv = ifTrue.asInstanceOf(InlineConditional.class)) != null) {
            // x ? (x? a: b): c === x ? a : c
            if (secondCv.condition.equals(condition)) {
                return conditionalValueConditionResolved(evaluationContext, condition, secondCv.ifTrue, ifFalse);
            }
            // x ? (!x ? a: b): c === x ? b : c
            if (secondCv.condition.equals(Negation.negate(evaluationContext, condition))) {
                return conditionalValueConditionResolved(evaluationContext, condition, secondCv.ifFalse, ifFalse);
            }
        }
        // x? a: (x? b:c) === x?a:c
        InlineConditional secondCv2;
        if ((secondCv2 = ifFalse.asInstanceOf(InlineConditional.class)) != null) {
            // x ? a: (x ? b:c) === x?a:c
            if (secondCv2.condition.equals(condition)) {
                return conditionalValueConditionResolved(evaluationContext, condition, ifTrue, secondCv2.ifFalse);
            }
            // x ? a: (!x ? b:c) === x?a:b
            if (secondCv2.condition.equals(Negation.negate(evaluationContext, condition))) {
                return conditionalValueConditionResolved(evaluationContext, condition, ifTrue, secondCv2.ifTrue);
            }
        }

        Expression edgeCase = edgeCases(evaluationContext, evaluationContext.getPrimitives(), condition, ifTrue, ifFalse);
        if (edgeCase != null) return builder.setExpression(edgeCase).build();

        // standardization... we swap!
        // this will result in  a != null ? a: x ==>  null == a ? x : a as the default form

        return builder.setExpression(new InlineConditional(evaluationContext.getAnalyserContext(),
                condition, ifTrue, ifFalse)).build();
        // TODO more advanced! if a "large" part of ifTrue or ifFalse appears in condition, we should create a temp variable
    }

    private static Expression edgeCases(EvaluationContext evaluationContext, Primitives primitives,
                                        Expression condition, Expression ifTrue, Expression ifFalse) {
        // x ? a : a == a
        if (ifTrue.equals(ifFalse)) return ifTrue;
        // a ? a : !a == a == !a ? !a : a
        if (condition.equals(ifTrue) && condition.equals(Negation.negate(evaluationContext, ifFalse))) {
            return new BooleanConstant(primitives, true);
        }
        // !a ? a : !a == !a == a ? !a : a --> will not happen, as we've already swapped

        // a ? true: b  --> a || b
        // a ? b : true --> !a || b
        // a ? b : false --> a && b
        // a ? false: b --> !a && b
        if (ifTrue instanceof BooleanConstant ifTrueBool) {
            if (ifTrueBool.constant()) {
                return new Or(primitives).append(evaluationContext, condition, ifFalse);
            }
            return new And(primitives).append(evaluationContext, Negation.negate(evaluationContext, condition), ifFalse);
        }
        if (ifFalse instanceof BooleanConstant ifFalseBool) {
            if (ifFalseBool.constant()) {
                return new Or(primitives).append(evaluationContext, Negation.negate(evaluationContext, condition), ifTrue);
            }
            return new And(primitives).append(evaluationContext, condition, ifTrue);
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
            Expression knownValue = new MethodCall(object, methodValue.methodInfo, methodValue.parameterExpressions);
            return inState(evaluationContext, knownValue) ? ifTrue : ifFalse;
        }
        return null;
    }


}
