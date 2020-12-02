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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.value.Instance;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.function.Predicate;

public class InlineConditionalOperator implements Expression {
    public final Expression condition;
    public final Expression ifTrue;
    public final Expression ifFalse;
    public final ObjectFlow objectFlow;

    public InlineConditionalOperator( Expression condition,
                                      Expression ifTrue,
                                      Expression ifFalse) {
        this(condition, ifTrue, ifFalse, ObjectFlow.NO_FLOW);
    }

    public InlineConditionalOperator( Expression condition,
                                      Expression ifTrue,
                                      Expression ifFalse,
                                     ObjectFlow objectFlow) {
        this.condition = Objects.requireNonNull(condition);
        this.ifFalse = Objects.requireNonNull(ifFalse);
        this.ifTrue = Objects.requireNonNull(ifTrue);
        this.objectFlow = Objects.requireNonNull(objectFlow);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InlineConditionalOperator that = (InlineConditionalOperator) o;
        return condition.equals(that.condition) &&
                ifTrue.equals(that.ifTrue) &&
                ifFalse.equals(that.ifFalse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(condition, ifTrue, ifFalse);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new InlineConditionalOperator(
                translationMap.translateExpression(condition),
                translationMap.translateExpression(ifTrue),
                translationMap.translateExpression(ifFalse));
    }


    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reCondition = condition.reEvaluate(evaluationContext, translation);
        EvaluationResult reTrue = ifTrue.reEvaluate(evaluationContext, translation);
        EvaluationResult reFalse = ifFalse.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder().compose(reCondition, reTrue, reFalse);
        EvaluationResult res = EvaluateInlineConditional.conditionalValueConditionResolved(
                evaluationContext, reCondition.value, reTrue.value, reFalse.value, objectFlow);
        return builder.setExpression(res.value).build();
    }


    @Override
    public int internalCompareTo(Expression v) {
        return condition.internalCompareTo(v);
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return condition.print(printMode) + "?" + ifTrue.print(printMode) + ":" + ifFalse.print(printMode);
    }

    private static final int NO_PATTERN = -2;

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        int inCondition = lookForPatterns(evaluationContext, variableProperty);
        if (inCondition != NO_PATTERN) return inCondition;
        return new MultiExpression(condition, ifTrue, ifFalse).getProperty(evaluationContext, variableProperty);
    }

    /*
    There are a few patterns that we can look out for.

    (1) a == null ? xx : a

    (3)-(4) same wit SIZE, e.g.
    (3) 0 == a.size() ? xx : a
    (4) (-3) + a.size() >= 0 ? a : x
     */
    private int lookForPatterns(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        if (variableProperty == VariableProperty.NOT_NULL) {
            Expression c = condition;
            boolean not = false;
            if (c.isInstanceOf(NegatedExpression.class)) {
                c = ((NegatedExpression) c).expression;
                not = true;
            }
            EqualsExpression equalsValue;
            if ((equalsValue = c.asInstanceOf(EqualsExpression.class)) != null && equalsValue.lhs.isInstanceOf(NullConstant.class)) {
                // null == rhs or not (null == rhs), now check that rhs appears left or right
                Expression rhs = equalsValue.rhs;
                if (ifTrue.equals(rhs)) {
                    // null == a ? a : something;  null != a ? a : something
                    return not ? evaluationContext.getProperty(ifFalse, variableProperty) : MultiLevel.NULLABLE;
                }
                if (ifFalse.equals(rhs)) {
                    // null == a ? something: a
                    return not ? MultiLevel.NULLABLE : evaluationContext.getProperty(ifTrue, variableProperty);
                }
            }
        }
        return NO_PATTERN;
    }

    @Override
    public Set<Variable> linkedVariables(EvaluationContext evaluationContext) {
        Set<Variable> result = null;
        for (Variable variable : variables()) {
            Set<Variable> links = evaluationContext.linkedVariables(variable);
            if (links == null) {
                return null;
            }
            if (result == null) {
                result = new HashSet<>(links);
            } else {
                result.addAll(links);
            }
        }
        return result;
    }

    @Override
    public int order() {
        return condition.order();
    }

    @Override
    public Instance getInstance(EvaluationContext evaluationContext) {
        if (Primitives.isPrimitiveExcludingVoid(type())) return null;
        return new Instance(type(), getObjectFlow(), EmptyExpression.EMPTY_EXPRESSION);
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            condition.visit(predicate);
            ifTrue.visit(predicate);
            ifFalse.visit(predicate);
        }
    }

    @Override
    public List<Variable> variables() {
        return ListUtil.immutableConcat(condition.variables(), ifTrue.variables(), ifFalse.variables());
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult conditionResult = condition.evaluate(evaluationContext, ForwardEvaluationInfo.NOT_NULL);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(conditionResult);

        // we'll want to evaluate in a different context, but pass on forward evaluation info to both
        EvaluationContext copyForThen = evaluationContext.child(conditionResult.value);
        EvaluationResult ifTrueResult = ifTrue.evaluate(copyForThen, forwardEvaluationInfo);
        builder.compose(ifTrueResult);

        EvaluationContext copyForElse = evaluationContext.child(NegatedExpression.negate(evaluationContext, conditionResult.value));
        EvaluationResult ifFalseResult = ifFalse.evaluate(copyForElse, forwardEvaluationInfo);
        builder.compose(ifFalseResult);

        if (conditionResult.value == EmptyExpression.NO_VALUE ||
                ifTrueResult.value == EmptyExpression.NO_VALUE ||
                ifFalseResult.value == EmptyExpression.NO_VALUE) {
            return builder.build();
        }
        // TODO ObjectFlow
        EvaluationResult cv = EvaluateInlineConditional.conditionalValueCurrentState(evaluationContext,
                conditionResult.value, ifTrueResult.value, ifFalseResult.value, ObjectFlow.NO_FLOW);
        return builder.setExpression(cv.value).compose(cv).build();
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
