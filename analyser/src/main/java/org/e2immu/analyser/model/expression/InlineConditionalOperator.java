/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.ConditionalValue;
import org.e2immu.analyser.model.abstractvalue.NegatedValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;

public class InlineConditionalOperator implements Expression {
    public final Expression condition;
    public final Expression ifTrue;
    public final Expression ifFalse;

    public InlineConditionalOperator(@NotNull Expression condition,
                                     @NotNull Expression ifTrue,
                                     @NotNull Expression ifFalse) {
        this.condition = Objects.requireNonNull(condition);
        this.ifFalse = Objects.requireNonNull(ifFalse);
        this.ifTrue = Objects.requireNonNull(ifTrue);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new InlineConditionalOperator(
                translationMap.translateExpression(condition),
                translationMap.translateExpression(ifTrue),
                translationMap.translateExpression(ifFalse));
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult conditionResult = condition.evaluate(evaluationContext, ForwardEvaluationInfo.NOT_NULL);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(conditionResult);

        // we'll want to evaluate in a different context, but pass on forward evaluation info to both
        EvaluationContext copyForThen = evaluationContext.child(conditionResult.value, null, false);
        EvaluationResult ifTrueResult = ifTrue.evaluate(copyForThen, forwardEvaluationInfo);
        builder.compose(ifTrueResult);
        builder.merge(copyForThen);

        EvaluationContext copyForElse = evaluationContext.child(NegatedValue.negate(conditionResult.value), null, false);
        EvaluationResult ifFalseResult = ifFalse.evaluate(copyForElse, forwardEvaluationInfo);
        builder.compose(ifFalseResult);
        builder.merge(copyForElse);

        // TODO ObjectFlow
        Value res = ConditionalValue.conditionalValueCurrentState(evaluationContext,
                conditionResult.value, ifTrueResult.value, ifFalseResult.value, ObjectFlow.NO_FLOW);
        builder.setValue(res);
        return builder.build();
    }

    @Override
    public ParameterizedType returnType() {
        return ifTrue.returnType();
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        return bracketedExpressionString(indent, condition) + " ? " + bracketedExpressionString(indent, ifTrue)
                + " : " + bracketedExpressionString(indent, ifFalse);
    }

    @Override
    public int precedence() {
        return 2;
    }


    @Override
    public List<? extends Element> subElements() {
        return List.of(condition, ifTrue, ifFalse);
    }
}
