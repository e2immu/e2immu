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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public EvaluationResult reEvaluate(EvaluationResult context, Map<Expression, Expression> translation, ForwardReEvaluationInfo forwardReEvaluationInfo) {
        EvaluationResult reValue = expression.reEvaluate(context, translation, forwardReEvaluationInfo);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context).compose(reValue);
        return builder.setExpression(Negation.negate(context, reValue.value())).build();
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = expression.translate(inspectionProvider, translationMap);
        if (translated == expression) return this;
        return new Negation(identifier, operator, translated);
    }

    public static Expression negate(EvaluationResult context, @NotNull Expression v) {
        Objects.requireNonNull(v);
        if (v instanceof BooleanConstant boolValue) {
            return boolValue.negate();
        }
        if (v instanceof Negatable negatable) {
            return negatable.negate();
        }
        if (v.isEmpty()) return v;

        if (v instanceof Negation negation) return negation.expression;
        if (v instanceof Or or) {
            Expression[] negated = or.expressions().stream()
                    .map(ov -> Negation.negate(context, ov))
                    .toArray(Expression[]::new);
            return And.and(context, negated);
        }
        if (v instanceof And and) {
            List<Expression> negated = and.getExpressions().stream()
                    .map(av -> Negation.negate(context, av)).toList();
            return Or.or(context, negated.toArray(Expression[]::new));
        }
        if (v instanceof Sum sum) {
            return sum.negate(context);
        }
        if (v instanceof GreaterThanZero greaterThanZero) {
            return greaterThanZero.negate(context);
        }

        if (v instanceof Equals equals) {
            if (equals.lhs instanceof InlineConditional inlineConditional) {
                EvaluationResult safeEvaluationContext = context.copyToPreventAbsoluteStateComputation();
                Expression result = Equals.tryToRewriteConstantEqualsInlineNegative(safeEvaluationContext, equals.rhs, inlineConditional);
                if (result != null) return result;
            }
            if (equals.rhs instanceof InlineConditional inlineConditional) {
                EvaluationResult safeEvaluationContext = context.copyToPreventAbsoluteStateComputation();
                Expression result = Equals.tryToRewriteConstantEqualsInlineNegative(safeEvaluationContext, equals.lhs, inlineConditional);
                if (result != null) return result;
            }
        }

        MethodInfo operator = v.isNumeric() ?
                context.getPrimitives().unaryMinusOperatorInt() :
                context.getPrimitives().logicalNotOperatorBool();
        return new Negation(Identifier.joined("neg", List.of(v.getIdentifier())), operator, v);
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
        return 0; // the negation wrapper has no other properties than "expression"
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return expression.variables(descendIntoFieldReferences);
    }


    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return context.evaluationContext().getProperty(expression, property, duringEvaluation, false);
    }

    @Override
    public ParameterizedType returnType() {
        return expression.returnType();
    }

    @Override
    public Expression removeAllReturnValueParts(Primitives primitives) {
        Expression value = expression.removeAllReturnValueParts(primitives);
        if (value == null) {
            if (expression.returnType().isBooleanOrBoxedBoolean()) {
                return new BooleanConstant(primitives, false);
            }
            return null; // numeric
        }
        return new Negation(identifier, operator, value);
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        if (expression.isDelayed()) {
            return new Negation(identifier, operator, expression.mergeDelays(causesOfDelay));
        }
        return this;
    }
}
