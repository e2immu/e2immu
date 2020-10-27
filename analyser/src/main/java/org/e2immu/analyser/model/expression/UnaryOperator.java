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

import com.github.javaparser.ast.expr.UnaryExpr;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.NegatedValue;
import org.e2immu.analyser.model.abstractvalue.UnknownPrimitiveValue;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * From https://introcs.cs.princeton.edu/java/11precedence/
 * <p>
 * precedence 15: ++, -- post-increment
 * precedence 14: ++, -- pre-increment, +, -, !, ~
 */
public class UnaryOperator implements Expression {
    public final Expression expression;
    public final int precedence;
    public final MethodInfo operator;

    public static final int PRECEDENCE_POST_INCREMENT = 15;
    public static final int DEFAULT_PRECEDENCE = 14;

    public UnaryOperator(@NotNull MethodInfo operator, @NotNull Expression expression, int precedence) {
        this.expression = Objects.requireNonNull(expression);
        this.precedence = precedence;
        this.operator = Objects.requireNonNull(operator);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnaryOperator that = (UnaryOperator) o;
        return expression.equals(that.expression) &&
                operator.equals(that.operator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression, operator);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new UnaryOperator(operator, translationMap.translateExpression(expression), precedence);
    }

    public static int precedence(@NotNull @NotModified UnaryExpr.Operator operator) {
        return switch (operator) {
            case POSTFIX_DECREMENT, POSTFIX_INCREMENT -> PRECEDENCE_POST_INCREMENT;
            default -> DEFAULT_PRECEDENCE;
        };
    }

    // NOTE: we're not visiting here!

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult evaluationResult = expression.evaluate(evaluationContext, ForwardEvaluationInfo.NOT_NULL);
        return new EvaluationResult.Builder(evaluationContext)
                .compose(evaluationResult)
                .setValue(computeValue(evaluationContext,
                        evaluationContext.getPrimitives(), evaluationResult))
                .build();
    }

    private Value computeValue(EvaluationContext evaluationContext, Primitives primitives, EvaluationResult evaluationResult) {
        Value v = evaluationResult.value;

        if (v.isUnknown()) return v;

        if (operator == primitives.logicalNotOperatorBool ||
                operator == primitives.unaryMinusOperatorInt) {
            return NegatedValue.negate(evaluationContext, v);
        }
        if (operator == primitives.unaryPlusOperatorInt) {
            return v;
        }
        if (operator == primitives.bitWiseNotOperatorInt) {
            if (v instanceof IntValue)
                return new IntValue(primitives, ~((IntValue) v).value, v.getObjectFlow());
            return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
        }
        if (operator == primitives.postfixDecrementOperatorInt
                || operator == primitives.prefixDecrementOperatorInt) {
            if (v instanceof IntValue)
                return new IntValue(primitives, ((IntValue) v).value - 1, v.getObjectFlow());
            return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
        }
        if (operator == primitives.postfixIncrementOperatorInt || operator == primitives.prefixIncrementOperatorInt) {
            if (v instanceof IntValue)
                return new IntValue(primitives, ((IntValue) v).value + 1, v.getObjectFlow());
            return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
        }
        throw new UnsupportedOperationException();
    }

    public static MethodInfo getOperator(@NotNull @NotModified Primitives primitives,
                                         @NotNull @NotModified UnaryExpr.Operator operator,
                                         @NotNull @NotModified TypeInfo typeInfo) {
        if (Primitives.isNumeric(typeInfo)) {
            switch (operator) {
                case MINUS:
                    return primitives.unaryMinusOperatorInt;
                case PLUS:
                    return primitives.unaryPlusOperatorInt;
                case BITWISE_COMPLEMENT:
                    return primitives.bitWiseNotOperatorInt;
                case POSTFIX_DECREMENT:
                    return primitives.postfixDecrementOperatorInt;
                case POSTFIX_INCREMENT:
                    return primitives.postfixIncrementOperatorInt;
                case PREFIX_DECREMENT:
                    return primitives.prefixDecrementOperatorInt;
                case PREFIX_INCREMENT:
                    return primitives.prefixIncrementOperatorInt;
                default:
            }
        }
        if (typeInfo == primitives.booleanTypeInfo || "java.lang.Boolean".equals(typeInfo.fullyQualifiedName)) {
            if (operator == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
                return primitives.logicalNotOperatorBool;
            }
        }
        throw new UnsupportedOperationException("?? unknown operator " + operator);
    }

    @Override
    public ParameterizedType returnType() {
        return expression.returnType();
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        if (precedence == 15) {
            return bracketedExpressionString(indent, expression) + operator.name;
        }
        return operator.name + bracketedExpressionString(indent, expression);
    }

    @Override
    public int precedence() {
        return precedence;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression);
    }
}
