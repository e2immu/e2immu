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

package org.e2immu.analyser.model.expression;

import com.github.javaparser.ast.expr.BinaryExpr;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.e2immu.analyser.model.expression.Precedence.*;

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
public class BinaryOperator extends ElementImpl implements Expression {
    protected final Primitives primitives;
    public final Expression lhs;
    public final Expression rhs;
    public final Precedence precedence;
    public final MethodInfo operator;

    public BinaryOperator(Identifier identifier,
                          Primitives primitives, Expression lhs, MethodInfo operator, Expression rhs, Precedence precedence) {
        super(identifier);
        this.lhs = Objects.requireNonNull(lhs);
        this.rhs = Objects.requireNonNull(rhs);
        this.precedence = precedence;
        this.operator = Objects.requireNonNull(operator);
        this.primitives = primitives;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BinaryOperator that = (BinaryOperator) o;
        return lhs.equals(that.lhs) &&
                rhs.equals(that.rhs) &&
                operator.equals(that.operator);
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        return UnknownExpression.primitiveGetProperty(property);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs, operator);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new BinaryOperator(identifier, primitives, translationMap.translateExpression(lhs),
                operator, translationMap.translateExpression(rhs), precedence);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_BINARY_OPERATOR; // not yet evaluated
    }

    @Override
    public List<Variable> variables() {
        return ListUtil.concatImmutable(lhs.variables(), rhs.variables());
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            lhs.visit(predicate);
            rhs.visit(predicate);
        }
    }

    // NOTE: we're not visiting here!

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        // we need to handle the short-circuit operators differently
        Primitives primitives = evaluationContext.getPrimitives();
        if (operator == primitives.orOperatorBool()) {
            return shortCircuit(evaluationContext, forwardEvaluationInfo, false);
        }
        if (operator == primitives.andOperatorBool()) {
            return shortCircuit(evaluationContext, forwardEvaluationInfo, true);
        }

        ForwardEvaluationInfo forward = allowsForNullOperands(primitives)
                ? forwardEvaluationInfo.copyDefault() : forwardEvaluationInfo.copyNotNull();
        EvaluationResult leftResult = lhs.evaluate(evaluationContext, forward);
        EvaluationResult rightResult = rhs.evaluate(evaluationContext, forward);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(leftResult, rightResult);
        builder.setExpression(determineValue(primitives, builder, leftResult, rightResult, evaluationContext));
        return builder.build();
    }

    private Expression determineValue(Primitives primitives,
                                      EvaluationResult.Builder builder,
                                      EvaluationResult left,
                                      EvaluationResult right,
                                      EvaluationContext evaluationContext) {
        Expression l = left.value();
        Expression r = right.value();

        if (operator == primitives.equalsOperatorObject()) {
            if (l.equals(r)) return new BooleanConstant(primitives, true);

            // HERE are the ==null checks
            if (l == NullConstant.NULL_CONSTANT && right.isNotNull0(false) ||
                    r == NullConstant.NULL_CONSTANT && left.isNotNull0(false)) {
                return new BooleanConstant(primitives, false);
            }
            // the following line ensures that a warning is sent when th ENN of a field/parameter is not NULLABLE
            // but the CNN is. The ENN trumps the annotation, but is not used in the computation of the constructor
            // see example in ExternalNotNull_0
            if (l == NullConstant.NULL_CONSTANT && right.isNotNull0(true) && r instanceof IsVariableExpression ve) {
                builder.setProperty(ve.variable(), Property.CANDIDATE_FOR_NULL_PTR_WARNING, Level.TRUE_DV);
            } else if (r == NullConstant.NULL_CONSTANT && left.isNotNull0(true) && l instanceof IsVariableExpression ve) {
                builder.setProperty(ve.variable(), Property.CANDIDATE_FOR_NULL_PTR_WARNING, Level.TRUE_DV);
            }
            return Equals.equals(identifier, evaluationContext, l, r);
        }
        if (operator == primitives.equalsOperatorInt()) {
            if (l.equals(r)) return new BooleanConstant(primitives, true);
            if (l == NullConstant.NULL_CONSTANT || r == NullConstant.NULL_CONSTANT) {
                // TODO need more resolution here to distinguish int vs Integer comparison throw new UnsupportedOperationException();
            }
            return Equals.equals(identifier, evaluationContext, l, r);
        }
        if (operator == primitives.notEqualsOperatorObject()) {
            if (l.equals(r)) new BooleanConstant(primitives, false);

            // HERE are the !=null checks
            if (l == NullConstant.NULL_CONSTANT && right.isNotNull0(false) ||
                    r == NullConstant.NULL_CONSTANT && left.isNotNull0(false)) {
                return new BooleanConstant(primitives, true);
            }
            return Negation.negate(evaluationContext,
                    Equals.equals(identifier, evaluationContext, l, r));
        }
        if (operator == primitives.notEqualsOperatorInt()) {
            if (l.equals(r)) return new BooleanConstant(primitives, false);
            if (l == NullConstant.NULL_CONSTANT || r == NullConstant.NULL_CONSTANT) {
                // TODO need more resolution throw new UnsupportedOperationException();
            }
            return Negation.negate(evaluationContext,
                    Equals.equals(identifier, evaluationContext, l, r));
        }

        // from here on, straightforward operations
        if (operator == primitives.plusOperatorInt()) {
            return Sum.sum(identifier, evaluationContext, l, r);
        }
        if (operator == primitives.minusOperatorInt()) {
            return Sum.sum(identifier, evaluationContext, l, Negation.negate(evaluationContext, r));
        }
        if (operator == primitives.multiplyOperatorInt()) {
            return Product.product(identifier, evaluationContext, l, r);
        }
        if (operator == primitives.divideOperatorInt()) {
            EvaluationResult er = Divide.divide(identifier, evaluationContext, l, r);
            builder.compose(er);
            return er.value();
        }
        if (operator == primitives.remainderOperatorInt()) {
            EvaluationResult er = Remainder.remainder(identifier, evaluationContext, l, r);
            builder.compose(er);
            return er.value();
        }
        if (operator == primitives.lessEqualsOperatorInt()) {
            return GreaterThanZero.less(identifier, evaluationContext, l, r, true);
        }
        if (operator == primitives.lessOperatorInt()) {
            return GreaterThanZero.less(identifier, evaluationContext, l, r, false);
        }
        if (operator == primitives.greaterEqualsOperatorInt()) {
            return GreaterThanZero.greater(identifier, evaluationContext, l, r, true);
        }
        if (operator == primitives.greaterOperatorInt()) {
            return GreaterThanZero.greater(identifier, evaluationContext, l, r, false);
        }
        if (operator == primitives.plusOperatorString()) {
            return StringConcat.stringConcat(identifier, evaluationContext, l, r);
        }

        // more obscure operators

        if (operator == primitives.xorOperatorBool()) {
            return BooleanXor.booleanXor(identifier, evaluationContext, l, r);
        }
        if (operator == primitives.bitwiseAndOperatorInt()) {
            return BitwiseAnd.bitwiseAnd(identifier, evaluationContext, l, r);
        }
        if (operator == primitives.bitwiseOrOperatorInt()) {
            return BitwiseOr.bitwiseOr(identifier, evaluationContext, l, r);
        }
        if (operator == primitives.bitwiseXorOperatorInt()) {
            return BitwiseXor.bitwiseXor(identifier, evaluationContext, l, r);
        }
        if (operator == primitives.leftShiftOperatorInt()) {
            return ShiftLeft.shiftLeft(identifier, evaluationContext, l, r);
        }
        if (operator == primitives.signedRightShiftOperatorInt()) {
            return SignedShiftRight.shiftRight(identifier, evaluationContext, l, r);
        }
        if (operator == primitives.unsignedRightShiftOperatorInt()) {
            return UnsignedShiftRight.unsignedShiftRight(identifier, evaluationContext, l, r);
        }
        throw new UnsupportedOperationException("Operator " + operator.fullyQualifiedName());
    }

    private EvaluationResult shortCircuit(EvaluationContext evaluationContext,
                                          ForwardEvaluationInfo forwardEvaluationInfo,
                                          boolean and) {
        ForwardEvaluationInfo forward = forwardEvaluationInfo.copyNotNull();
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        Primitives primitives = evaluationContext.getPrimitives();

        EvaluationResult l = lhs.evaluate(evaluationContext, forward);
        Expression constant = new BooleanConstant(primitives, !and);
        if (l.value().equals(constant)) {
            builder.raiseError(lhs.getIdentifier(), Message.Label.PART_OF_EXPRESSION_EVALUATES_TO_CONSTANT);
            return builder.compose(l).build();
        }

        Expression state = and ? l.value() : Negation.negate(evaluationContext, l.value());
        EvaluationContext child = evaluationContext.childState(state);
        assert child != null;
        EvaluationResult r = rhs.evaluate(child, forward);
        builder.compose(l, r);
        if (r.value() instanceof BooleanConstant booleanConstant) {
            builder.raiseError(rhs.getIdentifier(), Message.Label.PART_OF_EXPRESSION_EVALUATES_TO_CONSTANT);
            if (and && booleanConstant.getValue() || !and && !booleanConstant.getValue()) {
                // x && true, x || false
                builder.setExpression(l.value());
            } else {
                // x && false, x || true
                builder.setExpression(r.value());
            }
            return builder.build();
        }
        if (and) {
            builder.setExpression(And.and(evaluationContext, l.value(), r.value()));
        } else {
            builder.setExpression(Or.or(evaluationContext, l.value(), r.value()));
        }
        return builder.build();
    }

    private boolean allowsForNullOperands(Primitives primitives) {
        return operator == primitives.equalsOperatorInt() ||
                operator == primitives.equalsOperatorObject() ||
                operator == primitives.notEqualsOperatorObject() ||
                operator == primitives.notEqualsOperatorInt() ||
                operator == primitives.plusOperatorString();
    }

    @NotNull
    public static MethodInfo getOperator(@NotNull Primitives primitives,
                                         @NotNull @NotModified BinaryExpr.Operator operator,
                                         @NotModified TypeInfo widestType) {
        if (widestType == null || !widestType.isPrimitiveExcludingVoid()
                && !widestType.isBoxedExcludingVoid()) {
            if (operator == BinaryExpr.Operator.EQUALS) {
                return primitives.equalsOperatorObject();
            }
            if (operator == BinaryExpr.Operator.NOT_EQUALS) {
                return primitives.notEqualsOperatorObject();
            }
            if (widestType == primitives.stringTypeInfo() && operator == BinaryExpr.Operator.PLUS) {
                return primitives.plusOperatorString();
            }
            throw new UnsupportedOperationException("? what else can you have on " + widestType + ", operator " + operator);
        }
        if (widestType == primitives.booleanTypeInfo() || widestType.fullyQualifiedName.equals("java.lang.Boolean")) {
            switch (operator) {
                case XOR:
                    return primitives.xorOperatorBool();
                case BINARY_OR:
                case OR:
                    return primitives.orOperatorBool();
                case BINARY_AND:
                case AND:
                    return primitives.andOperatorBool();
                case EQUALS:
                    return primitives.equalsOperatorInt(); // TODO should clean up
                case NOT_EQUALS:
                    return primitives.notEqualsOperatorInt();
            }
            throw new UnsupportedOperationException("Operator " + operator + " on boolean");
        }
        if (widestType == primitives.charTypeInfo() || widestType.fullyQualifiedName.equals("java.lang.Character")) {
            switch (operator) {
                case PLUS:
                    return primitives.plusOperatorInt();
                case MINUS:
                    return primitives.minusOperatorInt();
                case EQUALS:
                    return primitives.equalsOperatorInt(); // TODO should clean up
                case NOT_EQUALS:
                    return primitives.notEqualsOperatorInt();
            }
            throw new UnsupportedOperationException("Operator " + operator + " on char");
        }
        if (widestType.isNumeric()) {
            switch (operator) {
                case MULTIPLY:
                    return primitives.multiplyOperatorInt();
                case REMAINDER:
                    return primitives.remainderOperatorInt();
                case DIVIDE:
                    return primitives.divideOperatorInt();
                case PLUS:
                    return primitives.plusOperatorInt();
                case MINUS:
                    return primitives.minusOperatorInt();
                case BINARY_OR:
                    return primitives.bitwiseOrOperatorInt();
                case BINARY_AND:
                    return primitives.bitwiseAndOperatorInt();
                case XOR:
                    return primitives.bitwiseXorOperatorInt();
                case UNSIGNED_RIGHT_SHIFT:
                    return primitives.unsignedRightShiftOperatorInt();
                case SIGNED_RIGHT_SHIFT:
                    return primitives.signedRightShiftOperatorInt();
                case LEFT_SHIFT:
                    return primitives.leftShiftOperatorInt();
                case GREATER:
                    return primitives.greaterOperatorInt();
                case GREATER_EQUALS:
                    return primitives.greaterEqualsOperatorInt();
                case LESS:
                    return primitives.lessOperatorInt();
                case LESS_EQUALS:
                    return primitives.lessEqualsOperatorInt();
                case EQUALS:
                    return primitives.equalsOperatorInt();
                case NOT_EQUALS:
                    return primitives.notEqualsOperatorInt();
            }
        }

        throw new UnsupportedOperationException("Unknown operator " + operator + " on widest type " +
                widestType.fullyQualifiedName);
    }

    public static MethodInfo fromAssignmentOperatorToNormalOperator(Primitives primitives, MethodInfo methodInfo) {
        if (primitives.assignOperatorInt() == methodInfo) return null;
        if (primitives.assignPlusOperatorInt() == methodInfo) return primitives.plusOperatorInt();
        if (primitives.assignMinusOperatorInt() == methodInfo) return primitives.minusOperatorInt();
        if (primitives.assignMultiplyOperatorInt() == methodInfo) return primitives.multiplyOperatorInt();
        if (primitives.assignDivideOperatorInt() == methodInfo) return primitives.divideOperatorInt();
        if (primitives.assignOrOperatorInt() == methodInfo) return primitives.bitwiseOrOperatorInt();
        if (primitives.assignAndOperatorInt() == methodInfo) return primitives.bitwiseAndOperatorInt();

        throw new UnsupportedOperationException("TODO! " + methodInfo.distinguishingName());
    }

    public static Precedence precedence(@NotNull Primitives primitives, @NotNull @NotModified MethodInfo methodInfo) {
        if (primitives.divideOperatorInt() == methodInfo || primitives.remainderOperatorInt() == methodInfo || primitives.multiplyOperatorInt() == methodInfo) {
            return MULTIPLICATIVE;
        }
        if (primitives.plusOperatorString() == methodInfo) {
            return STRING_CONCAT;
        }
        if (primitives.minusOperatorInt() == methodInfo || primitives.plusOperatorInt() == methodInfo) {
            return ADDITIVE;
        }
        if (primitives.signedRightShiftOperatorInt() == methodInfo || primitives.unsignedRightShiftOperatorInt() == methodInfo || primitives.leftShiftOperatorInt() == methodInfo) {
            return SHIFT;
        }
        if (primitives.greaterEqualsOperatorInt() == methodInfo || primitives.greaterOperatorInt() == methodInfo || primitives.lessEqualsOperatorInt() == methodInfo || primitives.lessOperatorInt() == methodInfo) {
            return RELATIONAL;
        }
        if (primitives.equalsOperatorInt() == methodInfo || primitives.equalsOperatorObject() == methodInfo || primitives.notEqualsOperatorInt() == methodInfo || primitives.notEqualsOperatorObject() == methodInfo) {
            return EQUALITY;
        }
        if (primitives.bitwiseAndOperatorInt() == methodInfo) {
            return AND;
        }
        if (primitives.bitwiseXorOperatorInt() == methodInfo) {
            return XOR;
        }
        if (primitives.bitwiseOrOperatorInt() == methodInfo) {
            return OR;
        }
        if (primitives.andOperatorBool() == methodInfo) {
            return LOGICAL_AND;
        }
        if (primitives.orOperatorBool() == methodInfo) {
            return LOGICAL_OR;
        }
        if (primitives.xorOperatorBool() == methodInfo) {
            return XOR;
        }
        throw new UnsupportedOperationException("? unknown operator " + methodInfo.distinguishingName());
    }

    // TODO needs cleanup
    @Override
    public ParameterizedType returnType() {
        Precedence precedence = precedence();
        return switch (precedence) {
            case RELATIONAL, LOGICAL_AND, LOGICAL_OR, EQUALITY, INSTANCE_OF -> primitives.booleanParameterizedType();
            case STRING_CONCAT -> primitives.stringParameterizedType();
            default -> primitives.widestType(lhs.returnType(), rhs.returnType());
        };
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(outputInParenthesis(qualification, precedence(), lhs))
                .add(Symbol.binaryOperator(operator.name))
                .add(outputInParenthesis(qualification, precedence(), rhs));
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public Precedence precedence() {
        return precedence;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(lhs, rhs);
    }

    @Override
    public int internalCompareTo(Expression v) {
        BinaryOperator b;
        if (v instanceof InlineConditional inlineConditional &&
                inlineConditional.condition instanceof BinaryOperator binaryOperator) {
            b = binaryOperator;
        } else if (v instanceof BinaryOperator binaryOperator) {
            b = binaryOperator;
        } else if (v instanceof GreaterThanZero gt0) {
            return compareBinaryToGt0(this, gt0);
        } else {
            int c = rhs.compareTo(v);
            if (c == 0) c = lhs.compareTo(v);
            return c;
        }
        int c0 = compareVariables(this, b);
        if (c0 != 0) return c0;

        // if there's a variable, it'll be in rhs
        // so priority is on the right hand side!!!
        int c = rhs.compareTo(b.rhs);
        if (c == 0) {
            c = lhs.compareTo(b.lhs);
        }
        return c;
    }

    public static int compareBinaryToGt0(BinaryOperator e1, GreaterThanZero e2) {
        int c = compareVariables(e1, e2);
        if (c != 0) return c;
        return -1;// binary operator (equals, e.g.) left of comparison
    }

    public static int compareVariables(Expression e1, Expression e2) {
        Set<Variable> myVariables = new HashSet<>(e1.variables());
        Set<Variable> otherVariables = new HashSet<>(e2.variables());
        int varDiff = myVariables.size() - otherVariables.size();
        if (varDiff != 0) return varDiff;
        String myVarStr = myVariables.stream().map(vv -> vv.fullyQualifiedName())
                .sorted().collect(Collectors.joining(","));
        String otherVarStr = otherVariables.stream().map(vv -> vv.fullyQualifiedName())
                .sorted().collect(Collectors.joining(","));
        return myVarStr.compareTo(otherVarStr);
    }

    @Override
    public Expression removeAllReturnValueParts() {
        boolean removeLhs = lhs.isReturnValue();
        boolean removeRhs = rhs.isReturnValue();
        if (removeLhs && removeRhs) return lhs; // nothing we can do
        if (removeLhs) return rhs;
        if (removeRhs) return lhs;
        return new BinaryOperator(identifier, primitives, lhs.removeAllReturnValueParts(),
                operator, rhs.removeAllReturnValueParts(), precedence);
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return lhs.causesOfDelay().merge(rhs.causesOfDelay());
    }
}
