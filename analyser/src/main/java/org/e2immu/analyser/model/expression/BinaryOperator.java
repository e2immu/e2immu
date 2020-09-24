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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.Message;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;

import static org.e2immu.analyser.parser.Primitives.PRIMITIVES;

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
@E2Container
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

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new BinaryOperator(translationMap.translateExpression(lhs),
                operator, translationMap.translateExpression(rhs), precedence);
    }

    // NOTE: we're not visiting here!

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        // we need to handle the short-circuit operators differently
        if (operator == PRIMITIVES.orOperatorBool) {
            return shortCircuit(evaluationContext, false);
        }
        if (operator == PRIMITIVES.andOperatorBool) {
            return shortCircuit(evaluationContext, true);
        }

        ForwardEvaluationInfo forward = allowsForNullOperands() ? ForwardEvaluationInfo.DEFAULT : ForwardEvaluationInfo.NOT_NULL;
        EvaluationResult leftResult = lhs.evaluate(evaluationContext, forward);
        EvaluationResult rightResult = rhs.evaluate(evaluationContext, forward);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(leftResult, rightResult);
        builder.setValue(determineValue(leftResult, rightResult, evaluationContext));
        return builder.build();
    }

    private Value determineValue(EvaluationResult left, EvaluationResult right, EvaluationContext evaluationContext) {
        Value l = left.value;
        Value r = right.value;

        if (l == UnknownValue.NO_VALUE || r == UnknownValue.NO_VALUE) return UnknownValue.NO_VALUE;
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;

        if (operator == PRIMITIVES.equalsOperatorObject) {
            if (l.equals(r)) return BoolValue.TRUE;

            // HERE are the ==null checks
            if (l == NullValue.NULL_VALUE && right.isNotNull0(evaluationContext) ||
                    r == NullValue.NULL_VALUE && left.isNotNull0(evaluationContext)) {
                return BoolValue.FALSE;
            }
            return EqualsValue.equals(l, r, booleanObjectFlow(evaluationContext));
        }
        if (operator == PRIMITIVES.equalsOperatorInt) {
            if (l.equals(r)) return BoolValue.TRUE;
            if (l == NullValue.NULL_VALUE || r == NullValue.NULL_VALUE) {
                // TODO need more resolution here to distinguish int vs Integer comparison throw new UnsupportedOperationException();
            }
            return EqualsValue.equals(l, r, booleanObjectFlow(evaluationContext));
        }
        if (operator == PRIMITIVES.notEqualsOperatorObject) {
            if (l.equals(r)) return BoolValue.FALSE;

            // HERE are the !=null checks
            if (l == NullValue.NULL_VALUE && right.isNotNull0(evaluationContext) ||
                    r == NullValue.NULL_VALUE && left.isNotNull0(evaluationContext)) {
                return BoolValue.TRUE;
            }
            return NegatedValue.negate(EqualsValue.equals(l, r, booleanObjectFlow(evaluationContext)));
        }
        if (operator == PRIMITIVES.notEqualsOperatorInt) {
            if (l.equals(r)) return BoolValue.FALSE;
            if (l == NullValue.NULL_VALUE || r == NullValue.NULL_VALUE) {
                // TODO need more resolution throw new UnsupportedOperationException();
            }
            return NegatedValue.negate(EqualsValue.equals(l, r, booleanObjectFlow(evaluationContext)));
        }

        // from here on, straightforward operations
        if (operator == PRIMITIVES.plusOperatorInt) {
            return SumValue.sum(l, r, intObjectFlow(evaluationContext));
        }
        if (operator == PRIMITIVES.minusOperatorInt) {
            return SumValue.sum(l, NegatedValue.negate(r), intObjectFlow(evaluationContext));
        }
        if (operator == PRIMITIVES.multiplyOperatorInt) {
            return ProductValue.product(l, r, intObjectFlow(evaluationContext));
        }
        if (operator == PRIMITIVES.divideOperatorInt) {
            return DivideValue.divide(evaluationContext, l, r, intObjectFlow(evaluationContext));
        }
        if (operator == PRIMITIVES.remainderOperatorInt) {
            return RemainderValue.remainder(evaluationContext, l, r, intObjectFlow(evaluationContext));
        }
        if (operator == PRIMITIVES.lessEqualsOperatorInt) {
            return GreaterThanZeroValue.less(l, r, true, booleanObjectFlow(evaluationContext));
        }
        if (operator == PRIMITIVES.lessOperatorInt) {
            return GreaterThanZeroValue.less(l, r, false, booleanObjectFlow(evaluationContext));
        }
        if (operator == PRIMITIVES.greaterEqualsOperatorInt) {
            return GreaterThanZeroValue.greater(l, r, true, booleanObjectFlow(evaluationContext));
        }
        if (operator == PRIMITIVES.greaterOperatorInt) {
            return GreaterThanZeroValue.greater(l, r, false, booleanObjectFlow(evaluationContext));
        }
        if (operator == PRIMITIVES.bitwiseAndOperatorInt) {
            return BitwiseAndValue.bitwiseAnd(l, r, intObjectFlow(evaluationContext));
        }
        /*
            if (operator == PRIMITIVES.bitwiseOrOperatorInt) {
                return new IntValue(l.toInt().value | r.toInt().value);
            }

            if (operator == PRIMITIVES.bitwiseXorOperatorInt) {
                return new IntValue(l.toInt().value ^ r.toInt().value);
            }
        }
         TODO
         */
        if (operator == PRIMITIVES.plusOperatorString) {
            return StringConcat.stringConcat(l, r, stringObjectFlow(evaluationContext));
        }
        throw new UnsupportedOperationException("Operator " + operator.fullyQualifiedName());
    }

    private ObjectFlow stringObjectFlow(EvaluationContext evaluationContext) {
        return new ObjectFlow(evaluationContext.getLocation(), PRIMITIVES.stringParameterizedType, Origin.RESULT_OF_OPERATOR);
    }

    private ObjectFlow booleanObjectFlow(EvaluationContext evaluationContext) {
        return new ObjectFlow(evaluationContext.getLocation(), PRIMITIVES.booleanParameterizedType, Origin.RESULT_OF_OPERATOR);
    }

    private ObjectFlow intObjectFlow(EvaluationContext evaluationContext) {
        return new ObjectFlow(evaluationContext.getLocation(), PRIMITIVES.intParameterizedType, Origin.RESULT_OF_OPERATOR);
    }

    private EvaluationResult shortCircuit(EvaluationContext evaluationContext, boolean and) {
        ForwardEvaluationInfo forward = ForwardEvaluationInfo.NOT_NULL;
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

        EvaluationResult l = lhs.evaluate(evaluationContext, forward);
        Value constant = and ? BoolValue.FALSE : BoolValue.TRUE;
        if (l.value == constant) {
            builder.raiseError(Message.PART_OF_EXPRESSION_EVALUATES_TO_CONSTANT);
            return builder.compose(l).build();
        }

        Value condition = and ? l.value : NegatedValue.negate(l.value);
        EvaluationContext child = evaluationContext.child(condition, null, false);
        EvaluationResult r = rhs.evaluate(child, forward);
        if (r.value == constant) {
            builder.raiseError(Message.PART_OF_EXPRESSION_EVALUATES_TO_CONSTANT);
            return builder.compose(l, r).build();
        }
        ObjectFlow objectFlow = new ObjectFlow(evaluationContext.getLocation(), PRIMITIVES.booleanParameterizedType, Origin.RESULT_OF_OPERATOR);
        if (and) {
            builder.setValue(new AndValue(objectFlow).append(l.value, r.value));
        } else {
            builder.setValue(new OrValue(objectFlow).append(l.value, r.value));
        }
        return builder.build();
    }

    private boolean allowsForNullOperands() {
        return operator == PRIMITIVES.equalsOperatorInt ||
                operator == PRIMITIVES.equalsOperatorObject ||
                operator == PRIMITIVES.notEqualsOperatorObject ||
                operator == PRIMITIVES.notEqualsOperatorInt ||
                operator == PRIMITIVES.plusOperatorString;
    }

    @NotNull
    public static MethodInfo getOperator(@NotNull @NotModified BinaryExpr.Operator operator,
                                         @NotModified TypeInfo widestType) {
        if (widestType == null || !widestType.isPrimitiveOrBoxed()) {
            if (operator == BinaryExpr.Operator.EQUALS) {
                return PRIMITIVES.equalsOperatorObject;
            }
            if (operator == BinaryExpr.Operator.NOT_EQUALS) {
                return PRIMITIVES.notEqualsOperatorObject;
            }
            if (widestType == PRIMITIVES.stringTypeInfo && operator == BinaryExpr.Operator.PLUS) {
                return PRIMITIVES.plusOperatorString;
            }
            throw new UnsupportedOperationException("? what else can you have on " + widestType + ", operator " + operator);
        }
        if (widestType == PRIMITIVES.booleanTypeInfo || widestType.fullyQualifiedName.equals("java.lang.Boolean")) {
            switch (operator) {
                case OR:
                    return PRIMITIVES.orOperatorBool;
                case AND:
                    return PRIMITIVES.andOperatorBool;
                case EQUALS:
                    return PRIMITIVES.equalsOperatorInt; // TODO should clean up
                case NOT_EQUALS:
                    return PRIMITIVES.notEqualsOperatorInt;
            }
            throw new UnsupportedOperationException("Operator " + operator + " on boolean");
        }
        if (widestType == PRIMITIVES.charTypeInfo || widestType.fullyQualifiedName.equals("java.lang.Character")) {
            switch (operator) {
                case PLUS:
                    return PRIMITIVES.plusOperatorInt;
                case MINUS:
                    return PRIMITIVES.minusOperatorInt;
                case EQUALS:
                    return PRIMITIVES.equalsOperatorInt; // TODO should clean up
                case NOT_EQUALS:
                    return PRIMITIVES.notEqualsOperatorInt;
            }
            throw new UnsupportedOperationException("Operator " + operator + " on char");
        }
        if (widestType.isNumericPrimitiveBoxed()) {
            switch (operator) {
                case MULTIPLY:
                    return PRIMITIVES.multiplyOperatorInt;
                case REMAINDER:
                    return PRIMITIVES.remainderOperatorInt;
                case DIVIDE:
                    return PRIMITIVES.divideOperatorInt;
                case PLUS:
                    return PRIMITIVES.plusOperatorInt;
                case MINUS:
                    return PRIMITIVES.minusOperatorInt;
                case BINARY_OR:
                    return PRIMITIVES.bitwiseOrOperatorInt;
                case BINARY_AND:
                    return PRIMITIVES.bitwiseAndOperatorInt;
                case XOR:
                    return PRIMITIVES.bitwiseXorOperatorInt;
                case UNSIGNED_RIGHT_SHIFT:
                    return PRIMITIVES.unsignedRightShiftOperatorInt;
                case SIGNED_RIGHT_SHIFT:
                    return PRIMITIVES.signedRightShiftOperatorInt;
                case LEFT_SHIFT:
                    return PRIMITIVES.leftShiftOperatorInt;
                case GREATER:
                    return PRIMITIVES.greaterOperatorInt;
                case GREATER_EQUALS:
                    return PRIMITIVES.greaterEqualsOperatorInt;
                case LESS:
                    return PRIMITIVES.lessOperatorInt;
                case LESS_EQUALS:
                    return PRIMITIVES.lessEqualsOperatorInt;
                case EQUALS:
                    return PRIMITIVES.equalsOperatorInt;
                case NOT_EQUALS:
                    return PRIMITIVES.notEqualsOperatorInt;
            }
        }

        throw new UnsupportedOperationException("Unknown operator " + operator + " on widest type " +
                widestType.fullyQualifiedName);
    }

    public static MethodInfo fromAssignmentOperatorToNormalOperator(MethodInfo methodInfo) {
        if (PRIMITIVES.assignOperatorInt == methodInfo) return null;
        if (PRIMITIVES.assignPlusOperatorInt == methodInfo) return PRIMITIVES.plusOperatorInt;
        if (PRIMITIVES.assignMinusOperatorInt == methodInfo) return PRIMITIVES.minusOperatorInt;
        if (PRIMITIVES.assignMultiplyOperatorInt == methodInfo) return PRIMITIVES.multiplyOperatorInt;
        if (PRIMITIVES.assignDivideOperatorInt == methodInfo) return PRIMITIVES.divideOperatorInt;
        if (PRIMITIVES.assignOrOperatorBoolean == methodInfo) return PRIMITIVES.orOperatorBool;

        throw new UnsupportedOperationException("TODO! " + methodInfo.distinguishingName());
    }

    public static int precedence(@NotNull @NotModified MethodInfo methodInfo) {
        if (PRIMITIVES.divideOperatorInt == methodInfo || PRIMITIVES.remainderOperatorInt == methodInfo || PRIMITIVES.multiplyOperatorInt == methodInfo) {
            return MULTIPLICATIVE_PRECEDENCE;
        }
        if (PRIMITIVES.minusOperatorInt == methodInfo || PRIMITIVES.plusOperatorInt == methodInfo || PRIMITIVES.plusOperatorString == methodInfo) {
            return ADDITIVE_PRECEDENCE;
        }
        if (PRIMITIVES.signedRightShiftOperatorInt == methodInfo || PRIMITIVES.unsignedRightShiftOperatorInt == methodInfo || PRIMITIVES.leftShiftOperatorInt == methodInfo) {
            return SHIFT_PRECEDENCE;
        }
        if (PRIMITIVES.greaterEqualsOperatorInt == methodInfo || PRIMITIVES.greaterOperatorInt == methodInfo || PRIMITIVES.lessEqualsOperatorInt == methodInfo || PRIMITIVES.lessOperatorInt == methodInfo) {
            return COMPARISON_PRECEDENCE;
        }
        if (PRIMITIVES.equalsOperatorInt == methodInfo || PRIMITIVES.equalsOperatorObject == methodInfo || PRIMITIVES.notEqualsOperatorInt == methodInfo || PRIMITIVES.notEqualsOperatorObject == methodInfo) {
            return EQUALITY_PRECEDENCE;
        }
        if (PRIMITIVES.bitwiseAndOperatorInt == methodInfo) {
            return AND_PRECEDENCE;
        }
        if (PRIMITIVES.bitwiseXorOperatorInt == methodInfo) {
            return XOR_PRECEDENCE;
        }
        if (PRIMITIVES.bitwiseOrOperatorInt == methodInfo) {
            return OR_PRECEDENCE;
        }
        if (PRIMITIVES.andOperatorBool == methodInfo) {
            return LOGICAL_AND_PRECEDENCE;
        }
        if (PRIMITIVES.orOperatorBool == methodInfo) {
            return LOGICAL_OR_PRECEDENCE;
        }
        throw new UnsupportedOperationException("? unknown operator " + methodInfo.distinguishingName());
    }

    // TODO needs cleanup
    @Override
    public ParameterizedType returnType() {
        if (operator == PRIMITIVES.equalsOperatorObject || operator == PRIMITIVES.notEqualsOperatorObject
                || operator == PRIMITIVES.equalsOperatorInt || operator == PRIMITIVES.notEqualsOperatorInt
                || operator == PRIMITIVES.lessEqualsOperatorInt || operator == PRIMITIVES.lessOperatorInt
                || operator == PRIMITIVES.greaterEqualsOperatorInt || operator == PRIMITIVES.greaterOperatorInt
                || operator == PRIMITIVES.orOperatorBool || operator == PRIMITIVES.andOperatorBool) {
            return PRIMITIVES.booleanParameterizedType;
        }
        return PRIMITIVES.widestType(lhs.returnType(), rhs.returnType());
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
    public List<? extends Element> subElements() {
        return List.of(lhs, rhs);
    }
}
