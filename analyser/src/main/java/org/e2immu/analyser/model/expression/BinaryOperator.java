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

import com.github.javaparser.ast.expr.BinaryExpr;
import com.google.common.collect.Sets;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.value.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * From https://introcs.cs.princeton.edu/java/11precedence/
 * All associativity is from left to right for binary operators: a+b+c = (a+b)+c
 * <p>
 * precedence 12: * / % multiplicative
 * precedence 11: + - additive, + string concat
 * precedence 10: >>>, <<< shift
 * precedence 9: <, <=, >, >= comparison
 * precedence 8: ==, != equality
 * precedence 7: & AND
 * precedence 6: ^ XOR
 * precedence 5: | OR
 * precedence 4: && logical AND
 * precedence 3: || logical OR
 */
@E2Immutable
public class BinaryOperator implements Expression {
    public final Expression lhs;
    public final Expression rhs;
    public final int precedence;
    public final MethodInfo operator;

    public static final int MULTIPLICATIVE_PRECEDENCE = 12;
    public static final int ADDITIVE_PRECEDENCE = 11;
    public static final int SHIFT_PRECEDENCE = 10;
    public static final int COMPARISON_PRECEDENCE = 9;
    public static final int EQUALITY_PRECEDENCE = 8;
    public static final int AND_PRECEDENCE = 7;
    public static final int XOR_PRECEDENCE = 6;
    public static final int OR_PRECEDENCE = 5;
    public static final int LOGICAL_AND_PRECEDENCE = 4;
    public static final int LOGICAL_OR_PRECEDENCE = 3;

    public BinaryOperator(@NotNull Expression lhs,
                          @NotNull MethodInfo operator,
                          @NotNull Expression rhs,
                          int precedence) {
        this.lhs = Objects.requireNonNull(lhs);
        this.rhs = Objects.requireNonNull(rhs);
        this.precedence = precedence;
        this.operator = Objects.requireNonNull(operator);
    }

    // NOTE: we're not visiting here!

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        ForwardEvaluationInfo forward = allowsForNullOperands() ? ForwardEvaluationInfo.DEFAULT : ForwardEvaluationInfo.NOT_NULL;

        Value l = lhs.evaluate(evaluationContext, visitor, forward);
        Value r = rhs.evaluate(evaluationContext, visitor, forward);
        if (l == UnknownValue.NO_VALUE || r == UnknownValue.NO_VALUE) return UnknownValue.NO_VALUE;

        if (operator == Primitives.PRIMITIVES.equalsOperatorObject) {
            if (l.equals(r)) return BoolValue.TRUE;
            if (l == NullValue.NULL_VALUE && r.isNotNull0(evaluationContext) ||
                    r == NullValue.NULL_VALUE && l.isNotNull0(evaluationContext)) {
                return BoolValue.FALSE;
            }
            return EqualsValue.equals(l, r);
        }
        if (operator == Primitives.PRIMITIVES.equalsOperatorInt) {
            if (l.equals(r)) return BoolValue.TRUE;
            if (l == NullValue.NULL_VALUE || r == NullValue.NULL_VALUE) {
                // TODO need more resolution here to distinguish int vs Integer comparison throw new UnsupportedOperationException();
            }
            return EqualsValue.equals(l, r);
        }
        if (operator == Primitives.PRIMITIVES.notEqualsOperatorObject) {
            if (l.equals(r)) return BoolValue.FALSE;
            if (l == NullValue.NULL_VALUE && r.isNotNull0(evaluationContext) ||
                    r == NullValue.NULL_VALUE && l.isNotNull0(evaluationContext)) {
                return BoolValue.TRUE;
            }
            return NegatedValue.negate(EqualsValue.equals(l, r));
        }
        if (operator == Primitives.PRIMITIVES.notEqualsOperatorInt) {
            if (l.equals(r)) return BoolValue.FALSE;
            if (l == NullValue.NULL_VALUE || r == NullValue.NULL_VALUE) {
                // TODO need more resolution throw new UnsupportedOperationException();
            }
            return NegatedValue.negate(EqualsValue.equals(l, r));
        }

        if (operator == Primitives.PRIMITIVES.orOperatorBool) {
            return new OrValue().append(List.of(l, r));
        }
        if (operator == Primitives.PRIMITIVES.andOperatorBool) {
            return new AndValue().append(l, r);
        }

        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;

        // from here on, straightforward operations
        if (operator == Primitives.PRIMITIVES.plusOperatorInt) {
            return SumValue.sum(l, r);
        }
        if (operator == Primitives.PRIMITIVES.minusOperatorInt) {
            return SumValue.sum(l, NegatedValue.negate(r));
        }
        if (operator == Primitives.PRIMITIVES.multiplyOperatorInt) {
            return ProductValue.product(l, r);
        }
        if (operator == Primitives.PRIMITIVES.divideOperatorInt) {
            return DivideValue.divide(evaluationContext, l, r);
        }
        if (operator == Primitives.PRIMITIVES.remainderOperatorInt) {
            return RemainderValue.remainder(evaluationContext, l, r);
        }
        if (operator == Primitives.PRIMITIVES.lessEqualsOperatorInt) {
            return GreaterThanZeroValue.less(l, r, true);
        }
        if (operator == Primitives.PRIMITIVES.lessOperatorInt) {
            return GreaterThanZeroValue.less(l, r, false);
        }
        if (operator == Primitives.PRIMITIVES.greaterEqualsOperatorInt) {
            return GreaterThanZeroValue.greater(l, r, true);
        }
        if (operator == Primitives.PRIMITIVES.greaterOperatorInt) {
            return GreaterThanZeroValue.greater(l, r, false);
        }
        if (operator == Primitives.PRIMITIVES.bitwiseAndOperatorInt) {
            return BitwiseAndValue.bitwiseAnd(l, r);
        }
        /*
            if (operator == Primitives.PRIMITIVES.bitwiseOrOperatorInt) {
                return new IntValue(l.toInt().value | r.toInt().value);
            }

            if (operator == Primitives.PRIMITIVES.bitwiseXorOperatorInt) {
                return new IntValue(l.toInt().value ^ r.toInt().value);
            }
        }
         TODO
         */
        if (operator == Primitives.PRIMITIVES.plusOperatorString) {
            return StringValue.concat(l, r);
        }
        throw new UnsupportedOperationException("Operator " + operator.fullyQualifiedName());
    }

    private boolean allowsForNullOperands() {
        return operator == Primitives.PRIMITIVES.equalsOperatorInt ||
                operator == Primitives.PRIMITIVES.equalsOperatorObject ||
                operator == Primitives.PRIMITIVES.notEqualsOperatorObject ||
                operator == Primitives.PRIMITIVES.notEqualsOperatorInt ||
                operator == Primitives.PRIMITIVES.plusOperatorString;
    }

    @NotNull
    public static MethodInfo getOperator(@NotNull @NotModified BinaryExpr.Operator operator,
                                         @NotModified TypeInfo widestType) {
        if (widestType == null || !widestType.isPrimitiveOrBoxed()) {
            if (operator == BinaryExpr.Operator.EQUALS) {
                return Primitives.PRIMITIVES.equalsOperatorObject;
            }
            if (operator == BinaryExpr.Operator.NOT_EQUALS) {
                return Primitives.PRIMITIVES.notEqualsOperatorObject;
            }
            if (widestType == Primitives.PRIMITIVES.stringTypeInfo && operator == BinaryExpr.Operator.PLUS) {
                return Primitives.PRIMITIVES.plusOperatorString;
            }
            throw new UnsupportedOperationException("? what else can you have on " + widestType + ", operator " + operator);
        }
        if (widestType == Primitives.PRIMITIVES.booleanTypeInfo || widestType.fullyQualifiedName.equals("java.lang.Boolean")) {
            switch (operator) {
                case OR:
                    return Primitives.PRIMITIVES.orOperatorBool;
                case AND:
                    return Primitives.PRIMITIVES.andOperatorBool;
                case EQUALS:
                    return Primitives.PRIMITIVES.equalsOperatorInt; // TODO should clean up
                case NOT_EQUALS:
                    return Primitives.PRIMITIVES.notEqualsOperatorInt;
            }
            throw new UnsupportedOperationException("Operator " + operator + " on boolean");
        }
        if (widestType == Primitives.PRIMITIVES.charTypeInfo || widestType.fullyQualifiedName.equals("java.lang.Character")) {
            switch (operator) {
                case PLUS:
                    return Primitives.PRIMITIVES.plusOperatorInt;
                case MINUS:
                    return Primitives.PRIMITIVES.minusOperatorInt;
                case EQUALS:
                    return Primitives.PRIMITIVES.equalsOperatorInt; // TODO should clean up
                case NOT_EQUALS:
                    return Primitives.PRIMITIVES.notEqualsOperatorInt;
            }
            throw new UnsupportedOperationException("Operator " + operator + " on char");
        }
        if (widestType.isNumericPrimitiveBoxed()) {
            switch (operator) {
                case MULTIPLY:
                    return Primitives.PRIMITIVES.multiplyOperatorInt;
                case REMAINDER:
                    return Primitives.PRIMITIVES.remainderOperatorInt;
                case DIVIDE:
                    return Primitives.PRIMITIVES.divideOperatorInt;
                case PLUS:
                    return Primitives.PRIMITIVES.plusOperatorInt;
                case MINUS:
                    return Primitives.PRIMITIVES.minusOperatorInt;
                case BINARY_OR:
                    return Primitives.PRIMITIVES.bitwiseOrOperatorInt;
                case BINARY_AND:
                    return Primitives.PRIMITIVES.bitwiseAndOperatorInt;
                case XOR:
                    return Primitives.PRIMITIVES.bitwiseXorOperatorInt;
                case GREATER:
                    return Primitives.PRIMITIVES.greaterOperatorInt;
                case GREATER_EQUALS:
                    return Primitives.PRIMITIVES.greaterEqualsOperatorInt;
                case LESS:
                    return Primitives.PRIMITIVES.lessOperatorInt;
                case LESS_EQUALS:
                    return Primitives.PRIMITIVES.lessEqualsOperatorInt;
                case EQUALS:
                    return Primitives.PRIMITIVES.equalsOperatorInt;
                case NOT_EQUALS:
                    return Primitives.PRIMITIVES.notEqualsOperatorInt;
            }
        }

        throw new UnsupportedOperationException("Unknown operator " + operator + " on widest type " +
                widestType.fullyQualifiedName);
    }

    public static int precedence(@NotNull @NotModified BinaryExpr.Operator operator) {
        switch (operator) {
            case DIVIDE:
            case REMAINDER:
            case MULTIPLY:
                return MULTIPLICATIVE_PRECEDENCE;
            case MINUS:
            case PLUS:
                return ADDITIVE_PRECEDENCE;
            case SIGNED_RIGHT_SHIFT:
            case UNSIGNED_RIGHT_SHIFT:
            case LEFT_SHIFT:
                return SHIFT_PRECEDENCE;
            case GREATER:
            case GREATER_EQUALS:
            case LESS:
            case LESS_EQUALS:
                return COMPARISON_PRECEDENCE;
            case EQUALS:
            case NOT_EQUALS:
                return EQUALITY_PRECEDENCE;
            case BINARY_AND:
                return AND_PRECEDENCE;
            case XOR:
                return XOR_PRECEDENCE;
            case BINARY_OR:
                return OR_PRECEDENCE;
            case AND:
                return LOGICAL_AND_PRECEDENCE;
            case OR:
                return LOGICAL_OR_PRECEDENCE;
        }
        throw new UnsupportedOperationException("? unknown operator " + operator);
    }

    // TODO needs cleanup
    @Override
    public ParameterizedType returnType() {
        if (operator == Primitives.PRIMITIVES.equalsOperatorObject || operator == Primitives.PRIMITIVES.notEqualsOperatorObject
                || operator == Primitives.PRIMITIVES.equalsOperatorInt || operator == Primitives.PRIMITIVES.notEqualsOperatorInt
                || operator == Primitives.PRIMITIVES.lessEqualsOperatorInt || operator == Primitives.PRIMITIVES.lessOperatorInt
                || operator == Primitives.PRIMITIVES.greaterEqualsOperatorInt || operator == Primitives.PRIMITIVES.greaterOperatorInt
                || operator == Primitives.PRIMITIVES.orOperatorBool || operator == Primitives.PRIMITIVES.andOperatorBool) {
            return Primitives.PRIMITIVES.booleanParameterizedType;
        }
        return Primitives.PRIMITIVES.widestType(lhs.returnType(), rhs.returnType());
    }

    @Override
    public String expressionString(int indent) {
        return bracketedExpressionString(indent, lhs) + " " + operator.name + " " + bracketedExpressionString(indent, rhs);
    }

    @Override
    public int precedence() {
        return precedence;
    }

    @Override
    @NotNull
    public Set<String> imports() {
        return Sets.union(lhs.imports(), rhs.imports());
    }

    @Override
    @NotNull
    public List<Expression> subExpressions() {
        return List.of(lhs, rhs);
    }

}
