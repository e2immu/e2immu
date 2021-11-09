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

import com.github.javaparser.ast.expr.UnaryExpr;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class UnaryOperator extends ElementImpl implements Expression {
    public final Expression expression;
    public final Precedence precedence;
    public final MethodInfo operator;

    public UnaryOperator(Identifier identifier,
                         @NotNull MethodInfo operator, @NotNull Expression expression, Precedence precedence) {
        super(identifier);
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
        return new UnaryOperator(identifier, operator, translationMap.translateExpression(expression), precedence);
    }

    @Override
    public int order() {
        throw new UnsupportedOperationException("Not yet evaluated: " + operator.name);
    }

    public static Precedence precedence(@NotNull @NotModified UnaryExpr.Operator operator) {
        return switch (operator) {
            case POSTFIX_DECREMENT, POSTFIX_INCREMENT, PLUS, MINUS -> Precedence.PLUSPLUS;
            default -> Precedence.UNARY;
        };
    }

    // NOTE: we're not visiting here!

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult evaluationResult = expression.evaluate(evaluationContext, forwardEvaluationInfo.copyNotNull());
        return new EvaluationResult.Builder(evaluationContext)
                .compose(evaluationResult)
                .setExpression(computeValue(evaluationContext, evaluationContext.getPrimitives(), evaluationResult))
                .build();
    }

    private Expression computeValue(EvaluationContext evaluationContext, Primitives primitives, EvaluationResult evaluationResult) {
        Expression v = evaluationResult.value();

        if (operator == primitives.logicalNotOperatorBool || operator == primitives.unaryMinusOperatorInt) {
            return Negation.negate(evaluationContext, v);
        }
        if (operator == primitives.unaryPlusOperatorInt) {
            return v;
        }
        if (operator == primitives.bitWiseNotOperatorInt) {
            if (v instanceof IntConstant ic)
                return new IntConstant(primitives, ~ic.constant());
            return new UnaryOperator(identifier, operator, v, precedence);
        }
        if (operator == primitives.postfixDecrementOperatorInt
                || operator == primitives.prefixDecrementOperatorInt) {
            if (v instanceof IntConstant ic)
                return new IntConstant(primitives, ic.constant() - 1);
            return new UnaryOperator(identifier, operator, v, precedence);
        }
        if (operator == primitives.postfixIncrementOperatorInt || operator == primitives.prefixIncrementOperatorInt) {
            if (v instanceof IntConstant ic)
                return new IntConstant(primitives, ic.constant() + 1);
            return new UnaryOperator(identifier, operator, v, precedence);
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
    public OutputBuilder output(Qualification qualification) {
        if (Primitives.isPostfix(operator)) {
            return new OutputBuilder().add(outputInParenthesis(qualification, precedence, expression))
                    .add(Symbol.plusPlusSuffix(operator.name));
        }
        return new OutputBuilder().add(Symbol.plusPlusPrefix(operator.name))
                .add(outputInParenthesis(qualification, precedence, expression));
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
        return List.of(expression);
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
        }
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        throw new UnsupportedOperationException("Not yet evaluated");
    }

    @Override
    public Expression removeAllReturnValueParts() {
        if( expression.isReturnValue()) return expression;
        return new UnaryOperator(identifier, operator, expression.removeAllReturnValueParts(), precedence);
    }
}
