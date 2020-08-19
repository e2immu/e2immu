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
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    public Expression translate(Map<? extends Variable, ? extends Variable> translationMap) {
        return new UnaryOperator(operator, expression.translate(translationMap), precedence);
    }

    public static int precedence(@NotNull @NotModified UnaryExpr.Operator operator) {
        switch (operator) {
            case POSTFIX_DECREMENT:
            case POSTFIX_INCREMENT:
                return PRECEDENCE_POST_INCREMENT;
            default:
                return DEFAULT_PRECEDENCE;
        }
    }

    // NOTE: we're not visiting here!

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        Value v = expression.evaluate(evaluationContext, visitor, ForwardEvaluationInfo.NOT_NULL);
        if (v.isUnknown()) return v;

        if (operator == Primitives.PRIMITIVES.logicalNotOperatorBool ||
                operator == Primitives.PRIMITIVES.unaryMinusOperatorInt) {
            return NegatedValue.negate(v);
        }
        if (operator == Primitives.PRIMITIVES.unaryPlusOperatorInt) {
            return v;
        }
        if (operator == Primitives.PRIMITIVES.bitWiseNotOperatorInt) {
            if (v instanceof IntValue)
                return new IntValue(~((IntValue) v).value);
            return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
        }
        if (operator == Primitives.PRIMITIVES.postfixDecrementOperatorInt
                || operator == Primitives.PRIMITIVES.prefixDecrementOperatorInt) {
            if (v instanceof IntValue)
                return new IntValue(((IntValue) v).value - 1);
            return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
        }
        if (operator == Primitives.PRIMITIVES.postfixIncrementOperatorInt || operator == Primitives.PRIMITIVES.prefixIncrementOperatorInt) {
            if (v instanceof IntValue)
                return new IntValue(((IntValue) v).value + 1);
            return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
        }
        throw new UnsupportedOperationException();
    }

    // TODO interesting case for null not allowed on typeInfo :-)
    public static MethodInfo getOperator(@NotNull @NotModified UnaryExpr.Operator operator, @NotNull @NotModified TypeInfo typeInfo) {
        if (typeInfo.isNumericPrimitive()) {
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
        if (typeInfo == Primitives.PRIMITIVES.booleanTypeInfo || "java.lang.Boolean".equals(typeInfo.fullyQualifiedName)) {
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
    public Set<TypeInfo> typesReferenced() {
        return expression.typesReferenced();
    }

    @Override
    @NotNull
    public List<Expression> subExpressions() {
        return List.of(expression);
    }

}
