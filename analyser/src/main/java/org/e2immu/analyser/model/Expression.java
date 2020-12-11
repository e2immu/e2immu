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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
        return this;
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

    default boolean isUnknown() {
        return false;
    }

    default boolean isDiscreteType() {
        ParameterizedType type = returnType();
        return type != null && Primitives.isDiscrete(type);
    }

    // only called from EvaluationContext.getProperty().
    // Use that method as the general way of obtaining a value for a property from a Value object
    // do NOT fall back on evaluationContext.getProperty(this, ...) because that'll be an infinite loop!

    default int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        throw new UnsupportedOperationException("For type " + getClass() + ", property " + variableProperty);
    }

    /**
     * @param evaluationContext to compute properties
     * @return null in case of delay
     */
    @Nullable
    @NotModified
    default Set<Variable> linkedVariables(EvaluationContext evaluationContext) {
        return Set.of();
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

    NewObject getInstance(EvaluationContext evaluationContext);

    default EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        Expression inMap = translation.get(this);
        return new EvaluationResult.Builder().setExpression(inMap == null ? this : inMap).build();
    }

    ObjectFlow getObjectFlow();

    /**
     * Tests the value first, and only if true, visit deeper.
     *
     * @param predicate return true if the search is to be continued deeper
     */
    default void visit(Predicate<Expression> predicate) {
        predicate.test(this);
    }


    default boolean isInstanceOf(Class<? extends Expression> clazz) {
        return clazz.isAssignableFrom(getClass());
    }

    default <T extends Expression> T asInstanceOf(Class<T> clazz) {
        if (clazz.isAssignableFrom(getClass())) {
            return (T) this;
        }
        return null;
    }

    default OutputBuilder outputInParenthesis(Precedence precedence, Expression expression) {
        if (precedence.greaterThan(expression.precedence())) {
            return new OutputBuilder().add(Symbol.LEFT_PARENTHESIS).add(expression.output()).add(Symbol.RIGHT_PARENTHESIS);
        }
        return expression.output();
    }

    default boolean hasBeenEvaluated() {
        return getObjectFlow() != ObjectFlow.NYE;
    }
}
