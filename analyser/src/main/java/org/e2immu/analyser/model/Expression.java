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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@E2Container
public interface Expression extends Element, Comparable<Expression> {

    @NotModified
    ParameterizedType returnType();

    @NotModified
    Precedence precedence();

    @NotModified
    EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo);

    @NotModified
    default List<LocalVariableReference> newLocalVariables() {
        return List.of();
    }

    @Override
    default Expression translate(TranslationMap translationMap) {
       throw new UnsupportedOperationException("all expressions need to have this implemented! "+getClass());
    }

    // ********************************

    int order();

    default boolean isNumeric() {
        return false;
    }

    @Override
    default int compareTo(Expression v) {
        return ExpressionComparator.SINGLETON.compare(this, v);
    }

    default int internalCompareTo(Expression v) {
        return 0;
    }

    default boolean isConstant() {
        return false;
    }

    // empty expressions are unknown! They should NOT appear in operations (binary, unary)
    default boolean isUnknown() {
        return false;
    }

    default boolean isDelayed(EvaluationContext evaluationContext) {
        return subElements().stream().anyMatch(element -> element instanceof Expression e && e.isDelayed(evaluationContext));
    }

    default boolean isDiscreteType() {
        ParameterizedType type = returnType();
        return type != null && Primitives.isDiscrete(type);
    }

    // only called from EvaluationContext.getProperty().
    // Use that method as the general way of obtaining a value for a property from a Value object
    // do NOT fall back on evaluationContext.getProperty(this, ...) because that'll be an infinite loop!

    default int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        throw new UnsupportedOperationException("For type " + getClass() + ", property " + variableProperty);
    }

    /**
     * @param evaluationContext to compute properties
     * @return null in case of delay
     */
    @Nullable
    @NotModified
    default LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        return LinkedVariables.EMPTY;
    }

    default boolean isNotNull() {
        Negation negatedValue = asInstanceOf(Negation.class);
        return negatedValue != null && negatedValue.expression.isInstanceOf(NullConstant.class);
    }

    default boolean isNull() {
        return isInstanceOf(NullConstant.class);
    }

    default boolean equalsNull() {
        if (this instanceof Negation) return false;
        Equals equals;
        if ((equals = asInstanceOf(Equals.class)) != null) {
            return equals.lhs.isNull();
        }
        return false;
    }

    default boolean equalsNotNull() {
        if (!(this instanceof Negation negation)) return false;
        Equals equals;
        if ((equals = negation.expression.asInstanceOf(Equals.class)) != null) {
            return equals.lhs.isNull();
        }
        return false;
    }

    default boolean isComputeProperties() {
        return !(this instanceof UnknownExpression);
    }

    default boolean isBoolValueTrue() {
        BooleanConstant boolValue;
        return ((boolValue = this.asInstanceOf(BooleanConstant.class)) != null) && boolValue.getValue();
    }

    default boolean isBoolValueFalse() {
        BooleanConstant boolValue;
        return ((boolValue = this.asInstanceOf(BooleanConstant.class)) != null) && !boolValue.getValue();
    }

    /*
    Construct an initial instance whose state can be enriched by companion methods.
     */
    default NewObject getInstance(EvaluationResult evaluationResult) {
        return null;
    }

    default EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        Expression inMap = translation.get(this);
        return new EvaluationResult.Builder().setExpression(inMap == null ? this : inMap).build();
    }

    /**
     * Tests the value first, and only if true, visit deeper.
     *
     * @param predicate return true if the search is to be continued deeper
     */
    default void visit(Predicate<Expression> predicate) {
        predicate.test(this);
    }

    default OutputBuilder outputInParenthesis(Qualification qualification, Precedence precedence, Expression expression) {
        if (precedence.greaterThan(expression.precedence())) {
            return new OutputBuilder().add(Symbol.LEFT_PARENTHESIS).add(expression.output(qualification)).add(Symbol.RIGHT_PARENTHESIS);
        }
        return expression.output(qualification);
    }

    default boolean isInitialReturnExpression() {
        return this instanceof UnknownExpression unknownExpression && unknownExpression.msg().equals(UnknownExpression.RETURN_VALUE);
    }

    default boolean isBooleanConstant() {
        return isInstanceOf(BooleanConstant.class);
    }
}
