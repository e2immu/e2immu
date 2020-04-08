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
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * From https://introcs.cs.princeton.edu/java/11precedence/
 * <p>
 * precedence 15: ++, -- post-increment
 * precedence 14: ++, -- pre-increment, +, -, !, ~
 */
@E2Immutable
public class UnaryOperator implements Expression {
    public final Expression expression;
    public final int precedence;
    public final MethodInfo operator;

    public UnaryOperator(@NullNotAllowed MethodInfo operator, @NullNotAllowed Expression expression, int precedence) {
        this.expression = Objects.requireNonNull(expression);
        this.precedence = precedence;
        this.operator = Objects.requireNonNull(operator);
    }

    public static int precedence(@NullNotAllowed UnaryExpr.Operator operator) {
        switch (operator) {
            case POSTFIX_DECREMENT:
            case POSTFIX_INCREMENT:
                return 15;
            default:
                return 14;
        }
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext) {
        Value v = expression.evaluate(evaluationContext);
        if (v == UnknownValue.UNKNOWN_VALUE) return v;

        if (operator == Primitives.PRIMITIVES.logicalNotOperatorBool) {
            return NegatedValue.negate(v);
        }
        if (operator == Primitives.PRIMITIVES.unaryPlusOperatorInt) {
            return v;
        }
        if (operator == Primitives.PRIMITIVES.unaryMinusOperatorInt) {
            return new IntValue(-((IntValue) v).value);
        }
        if (operator == Primitives.PRIMITIVES.bitWiseNotOperatorInt) {
            return new IntValue(~((IntValue) v).value);
        }
        if (operator == Primitives.PRIMITIVES.postfixDecrementOperatorInt || operator == Primitives.PRIMITIVES.prefixDecrementOperatorInt) {
            return new IntValue(((IntValue) v).value - 1);
        }
        if (operator == Primitives.PRIMITIVES.postfixIncrementOperatorInt || operator == Primitives.PRIMITIVES.prefixIncrementOperatorInt) {
            if (v instanceof NumericValue)
                return new IntValue(((IntValue) v).value + 1);
            return UnknownValue.UNKNOWN_VALUE;
        }
        throw new UnsupportedOperationException();
    }

    // TODO interesting case for null not allowed on typeInfo :-)
    public static MethodInfo getOperator(@NullNotAllowed UnaryExpr.Operator operator, @NullNotAllowed TypeInfo typeInfo) {
        if (typeInfo == Primitives.PRIMITIVES.intTypeInfo) {
            switch (operator) {
                case MINUS:
                    return Primitives.PRIMITIVES.unaryMinusOperatorInt;
                case PLUS:
                    return Primitives.PRIMITIVES.unaryPlusOperatorInt;
                case BITWISE_COMPLEMENT:
                    return Primitives.PRIMITIVES.bitWiseNotOperatorInt;
                case POSTFIX_DECREMENT:
                    return Primitives.PRIMITIVES.postfixDecrementOperatorInt;
                case POSTFIX_INCREMENT:
                    return Primitives.PRIMITIVES.postfixIncrementOperatorInt;
                case PREFIX_DECREMENT:
                    return Primitives.PRIMITIVES.prefixDecrementOperatorInt;
                case PREFIX_INCREMENT:
                    return Primitives.PRIMITIVES.prefixIncrementOperatorInt;
                default:
            }
        }
        if (typeInfo == Primitives.PRIMITIVES.booleanTypeInfo) {
            if (operator == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
                return Primitives.PRIMITIVES.logicalNotOperatorBool;
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
    public Set<String> imports() {
        return expression.imports();
    }

    @Override
    @NotNull
    public List<Expression> subExpressions() {
        return List.of(expression);
    }

}
