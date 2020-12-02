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

package org.e2immu.analyser.model.expression.util;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ShallowTypeAnalyser;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.value.Filter;
import org.e2immu.analyser.model.value.MethodValue;
import org.e2immu.analyser.model.value.VariableValue;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;

public class EvaluateInlineConditional {

    public static EvaluationResult conditionalValueCurrentState(EvaluationContext evaluationContext, Expression conditionBeforeState, Expression ifTrue, Expression ifFalse, ObjectFlow objectFlow) {
        Value condition = checkState(evaluationContext,
                evaluationContext.getPrimitives(),
                evaluationContext.getConditionManager().state, conditionBeforeState);
        return conditionalValueConditionResolved(evaluationContext, condition, ifTrue, ifFalse, objectFlow);
    }

    public static EvaluationResult conditionalValueConditionResolved(EvaluationContext evaluationContext, Expression condition, Expression ifTrue, Expression ifFalse, ObjectFlow objectFlow) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        if (condition instanceof BooleanConstant bc) {
            boolean first = bc.constant();
            builder.raiseError(Message.INLINE_CONDITION_EVALUATES_TO_CONSTANT);
            return builder.setExpression(first ? ifTrue : ifFalse).build();
        }

        // not x ? a: b --> x ? b: a
        NegatedExpression negatedCondition;
        if ((negatedCondition = condition.asInstanceOf(NegatedExpression.class)) != null) {
            return conditionalValueConditionResolved(evaluationContext, negatedCondition.expression, ifFalse, ifTrue, objectFlow);
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

        InlineConditionalOperator secondCv;
        // x ? (x? a: b): c == x ? a : c
        if ((secondCv = ifTrue.asInstanceOf(InlineConditionalOperator.class)) != null && secondCv.condition.equals(condition)) {
            return conditionalValueConditionResolved(evaluationContext, condition, secondCv.ifTrue, ifFalse, objectFlow);
        }

        Expression edgeCase = edgeCases(evaluationContext, evaluationContext.getPrimitives(), condition, ifTrue, ifFalse);
        if (edgeCase != null) return builder.setExpression(edgeCase).build();

        // standardization... we swap!
        // this will result in  a != null ? a: x ==>  null == a ? x : a as the default form

        return builder.setExpression(new InlineConditionalOperator(condition, ifTrue, ifFalse, objectFlow)).build();
        // TODO more advanced! if a "large" part of ifTrue or ifFalse appears in condition, we should create a temp variable
    }

    private static Expression checkState(EvaluationContext evaluationContext, Primitives primitives, Expression state, Expression condition) {
        if (state == EmptyExpression.EMPTY_EXPRESSION) return condition;
        Expression and = new AndExpression(primitives).append(evaluationContext, state, condition);
        if (and.equals(condition)) {
            return new BooleanConstant(primitives, true);
        }
        if (and instanceof BooleanConstant) return and;
        return condition;
    }

    private static Expression edgeCases(EvaluationContext evaluationContext, Primitives primitives,
                                        Expression condition, Expression ifTrue, Expression ifFalse) {
        // x ? a : a == a
        if (ifTrue.equals(ifFalse)) return ifTrue;
        // a ? a : !a == a == !a ? !a : a
        if (condition.equals(ifTrue) && condition.equals(NegatedExpression.negate(evaluationContext, ifFalse))) {
            return new BooleanConstant(primitives, true);
        }
        // !a ? a : !a == !a == a ? !a : a --> will not happen, as we've already swapped

        // a ? true: b  --> a || b
        // a ? b : true --> !a || b
        // a ? b : false --> a && b
        // a ? false: b --> !a && b
        if (ifTrue instanceof BooleanConstant ifTrueBool) {
            if (ifTrueBool.constant()) {
                return new OrExpression(primitives).append(evaluationContext, condition, ifFalse);
            }
            return new AndExpression(primitives).append(evaluationContext, NegatedExpression.negate(evaluationContext, condition), ifFalse);
        }
        if (ifFalse instanceof BooleanConstant ifFalseBool) {
            if (ifFalseBool.constant()) {
                return new OrExpression(primitives).append(evaluationContext, NegatedExpression.negate(evaluationContext, condition), ifTrue);
            }
            return new AndExpression(primitives).append(evaluationContext, condition, ifTrue);
        }
        return null;
    }

    // isFact(contains(e)) will check if "contains(e)" is part of the current instance's state
    // it will bypass ConditionalValue and return ifTrue or ifFalse accordingly
    // note that we don't need to check for !isFact() because the inversion has already taken place
    private static Expression isFact(EvaluationContext evaluationContext, Expression condition, Expression ifTrue, Expression ifFalse) {
        if (condition instanceof MethodValue methodValue && ShallowTypeAnalyser.IS_FACT_FQN.equals(methodValue.methodInfo.fullyQualifiedName)) {
            return inState(evaluationContext, methodValue.parameters.get(0)) ? ifTrue : ifFalse;
        }
        return null;
    }

    private static boolean inState(EvaluationContext evaluationContext, Expression expression) {
        Filter.FilterResult<Expression> res = Filter.filter(evaluationContext, evaluationContext.getConditionManager().state,
                Filter.FilterMode.ACCEPT, new Filter.ExactValue(expression));
        return !res.accepted().isEmpty();
    }

    // whilst isKnown is also caught at the level of MethodCall, we grab it here to avoid warnings for
    // constant evaluation
    private static Expression isKnown(EvaluationContext evaluationContext, Expression condition, Expression ifTrue, Expression ifFalse) {
        if (condition instanceof MethodValue methodValue &&
                ShallowTypeAnalyser.IS_KNOWN_FQN.equals(methodValue.methodInfo.fullyQualifiedName) &&
                methodValue.parameters.get(0) instanceof BooleanConstant boolValue && boolValue.constant()) {
            VariableValue object = new VariableValue(new This(evaluationContext.getAnalyserContext(), methodValue.methodInfo.typeInfo));
            Expression knownValue = new MethodValue(methodValue.methodInfo, object, methodValue.parameters, methodValue.objectFlow);
            return inState(evaluationContext, knownValue) ? ifTrue : ifFalse;
        }
        return null;
    }


}
