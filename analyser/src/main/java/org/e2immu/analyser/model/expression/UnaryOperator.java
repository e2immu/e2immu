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
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.delay.SingleDelay;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class UnaryOperator extends BaseExpression implements Expression {
    public final Expression expression;
    public final Precedence precedence;
    public final MethodInfo operator;
    public static final int COMPLEXITY = 2;

    public UnaryOperator(Identifier identifier,
                         @NotNull MethodInfo operator, @NotNull Expression expression, Precedence precedence) {
        super(identifier, COMPLEXITY + expression.getComplexity());
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
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = expression.translate(inspectionProvider, translationMap);
        if (translated == expression) return this;
        return new UnaryOperator(identifier, operator, translated, precedence);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_UNARY_OPERATOR; // not yet evaluated
    }

    public static Precedence precedence(@NotNull @NotModified UnaryExpr.Operator operator) {
        return switch (operator) {
            case POSTFIX_DECREMENT, POSTFIX_INCREMENT, PLUS, MINUS -> Precedence.PLUSPLUS;
            default -> Precedence.UNARY;
        };
    }

    // NOTE: we're not visiting here!

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult evaluationResult = expression.evaluate(context, forwardEvaluationInfo.notNullNotAssignment());
        Expression value = computeValue(context, context.getPrimitives(), evaluationResult);
        return new EvaluationResult.Builder(context)
                .compose(evaluationResult)
                .setExpression(value)
                .build();
    }

    private Expression computeValue(EvaluationResult context, Primitives primitives, EvaluationResult evaluationResult) {
        Expression v = evaluationResult.value();

        if (operator == primitives.logicalNotOperatorBool() || operator == primitives.unaryMinusOperatorInt()) {
            return Negation.negate(context, v);
        }
        if (operator == primitives.unaryPlusOperatorInt()) {
            return v;
        }
        if (operator == primitives.bitWiseNotOperatorInt()) {
            if (v instanceof IntConstant ic)
                return new IntConstant(primitives, ~ic.constant());
            return new UnaryOperator(identifier, operator, v, precedence);
        }
        if (operator == primitives.postfixDecrementOperatorInt()
                || operator == primitives.prefixDecrementOperatorInt()) {
            if (v instanceof IntConstant ic)
                return new IntConstant(primitives, ic.constant() - 1);
            return new UnaryOperator(identifier, operator, v, precedence);
        }
        if (operator == primitives.postfixIncrementOperatorInt() || operator == primitives.prefixIncrementOperatorInt()) {
            if (v instanceof IntConstant ic)
                return new IntConstant(primitives, ic.constant() + 1);
            return new UnaryOperator(identifier, operator, v, precedence);
        }
        throw new UnsupportedOperationException();
    }

    public static MethodInfo getOperator(@NotNull @NotModified Primitives primitives,
                                         @NotNull @NotModified UnaryExpr.Operator operator,
                                         @NotNull @NotModified TypeInfo typeInfo) {
        if (typeInfo.isNumeric()) {
            switch (operator) {
                case MINUS:
                    return primitives.unaryMinusOperatorInt();
                case PLUS:
                    return primitives.unaryPlusOperatorInt();
                case BITWISE_COMPLEMENT:
                    return primitives.bitWiseNotOperatorInt();
                case POSTFIX_DECREMENT:
                    return primitives.postfixDecrementOperatorInt();
                case POSTFIX_INCREMENT:
                    return primitives.postfixIncrementOperatorInt();
                case PREFIX_DECREMENT:
                    return primitives.prefixDecrementOperatorInt();
                case PREFIX_INCREMENT:
                    return primitives.prefixIncrementOperatorInt();
                default:
            }
        }
        if (typeInfo == primitives.booleanTypeInfo() || "java.lang.Boolean".equals(typeInfo.fullyQualifiedName)) {
            if (operator == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
                return primitives.logicalNotOperatorBool();
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
        if (operator.isPostfix()) {
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
    public CausesOfDelay causesOfDelay() {
        return expression.causesOfDelay();
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        if (expression.isDelayed()) {
            return new UnaryOperator(identifier, operator, expression.mergeDelays(causesOfDelay), precedence);
        }
        return this;
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return DelayFactory.createDelay(context.evaluationContext().getLocation(duringEvaluation?Stage.EVALUATION: Stage.MERGE),
                CauseOfDelay.Cause.VALUE);
    }
}
