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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Negation extends UnaryOperator implements ExpressionWrapper {

    public Expression getExpression() {
        return expression;
    }

    @Override
    public int wrapperOrder() {
        return WRAPPER_ORDER_NEGATED;
    }

    private Negation(Identifier identifier, MethodInfo operator, Expression value) {
        super(identifier, operator, value, value.isNumeric() ? Precedence.PLUSPLUS : Precedence.UNARY);
        if (value.isInstanceOf(Negation.class)) throw new UnsupportedOperationException();
    }

    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reValue = expression.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder().compose(reValue);
        return builder.setExpression(Negation.negate(evaluationContext, reValue.value())).build();
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        Expression translated = expression.translate(translationMap);
        if (translated == expression) return this;
        return new Negation(identifier, operator, translated);
    }

    public static Expression negate(EvaluationContext evaluationContext, @NotNull Expression v) {
        Objects.requireNonNull(v);
        if (v instanceof BooleanConstant boolValue) {
            return boolValue.negate();
        }
        if (v instanceof Negatable negatable) {
            return negatable.negate();
        }
        if (v.isUnknown()) return v;

        if (v instanceof Negation negation) return negation.expression;
        if (v instanceof Or or) {
            Expression[] negated = or.expressions().stream()
                    .map(ov -> Negation.negate(evaluationContext, ov))
                    .toArray(Expression[]::new);
            return And.and(evaluationContext, negated);
        }
        if (v instanceof And and) {
            List<Expression> negated = and.getExpressions().stream()
                    .map(av -> Negation.negate(evaluationContext, av)).collect(Collectors.toList());
            return Or.or(evaluationContext, negated.toArray(Expression[]::new));
        }
        if (v instanceof Sum sum) {
            return sum.negate(evaluationContext);
        }
        if (v instanceof GreaterThanZero greaterThanZero) {
            return greaterThanZero.negate(evaluationContext);
        }

        if (v instanceof Equals equals) {
            if (equals.lhs instanceof InlineConditional inlineConditional) {
                Expression result = Equals.tryToRewriteConstantEqualsInlineNegative(evaluationContext, equals.rhs, inlineConditional);
                if (result != null) return result;
            }
            if (equals.rhs instanceof InlineConditional inlineConditional) {
                Expression result = Equals.tryToRewriteConstantEqualsInlineNegative(evaluationContext, equals.lhs, inlineConditional);
                if (result != null) return result;
            }
        }

        MethodInfo operator = v.isNumeric() ?
                evaluationContext.getPrimitives().unaryMinusOperatorInt() :
                evaluationContext.getPrimitives().logicalNotOperatorBool();
        return new Negation(Identifier.generate(), operator, v);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Negation that = (Negation) o;
        return expression.equals(that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        // we intercept a few standard cases... too ugly otherwise
        if (expression instanceof Equals equals) {
            return new OutputBuilder().add(outputInParenthesis(qualification, equals.precedence(), equals.lhs))
                    .add(Symbol.NOT_EQUALS)
                    .add(outputInParenthesis(qualification, equals.precedence(), equals.rhs));
        }
        return new OutputBuilder().add(expression.isNumeric() ? Symbol.UNARY_MINUS : Symbol.UNARY_BOOLEAN_NOT)
                .add(outputInParenthesis(qualification, precedence(), expression));
    }

    @Override
    public int internalCompareTo(Expression v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return expression.variables(descendIntoFieldReferences);
    }


    @Override
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        return evaluationContext.getProperty(expression, property, duringEvaluation, false);
    }

    @Override
    public ParameterizedType returnType() {
        return expression.returnType();
    }

    @Override
    public Expression removeAllReturnValueParts() {
        if (expression.isReturnValue()) return expression;
        return new Negation(identifier, operator, expression.removeAllReturnValueParts());
    }
}
