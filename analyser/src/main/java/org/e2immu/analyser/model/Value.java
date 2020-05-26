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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.abstractvalue.NegatedValue;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.annotation.NotModified;

import java.util.Map;
import java.util.Set;

/**
 * Shared properties: @NotNull(n), dynamic type properties (@Immutable(n), @Container)
 * Properties of variables are ALWAYS computed inside an evaluation context; properties of methods come from outside the scope only.
 */
public interface Value extends Comparable<Value> {
    int ORDER_CONSTANT_NULL = 30;
    int ORDER_CONSTANT_BOOLEAN = 31;
    int ORDER_CONSTANT_BYTE = 32;
    int ORDER_CONSTANT_CHAR = 33;
    int ORDER_CONSTANT_SHORT = 34;
    int ORDER_CONSTANT_INT = 35;
    int ORDER_CONSTANT_FLOAT = 36;
    int ORDER_CONSTANT_LONG = 37;
    int ORDER_CONSTANT_DOUBLE = 38;
    int ORDER_CONSTANT_CLASS = 39;
    int ORDER_CONSTANT_STRING = 40;
    int ORDER_ARRAY = 42;
    int ORDER_PRIMITIVE = 41;
    int ORDER_INSTANCE_OF = 43;
    int ORDER_PRODUCT = 45;
    int ORDER_DIVIDE = 46;
    int ORDER_REMAINDER = 47;
    int ORDER_SUM = 48;
    int ORDER_GEQ0 = 49;
    int ORDER_AND = 50;
    int ORDER_OR = 51;
    int ORDER_EQUALS = 53;
    int ORDER_NEGATED = 54;
    int ORDER_BITWISE_AND = 55;
    int ORDER_CONSTRAINED_NUMERIC_VALUE = 56;
    int ORDER_CONDITIONAL = 60;
    int ORDER_INSTANCE = 71;
    int ORDER_METHOD = 70;
    int ORDER_VARIABLE_VALUE = 80;
    int ORDER_PARAMETER = 81;
    int ORDER_COMBINED = 82;
    int ORDER_TYPE = 90;
    int ORDER_NO_VALUE = 100;

    int order();

    @Override
    default int compareTo(Value v) {
        // negations are always AFTER their argument
        if (v instanceof NegatedValue && !(this instanceof NegatedValue)) {
            return -1;
        }
        if (this instanceof NegatedValue && !(v instanceof NegatedValue)) {
            return 1;
        }
        if (getClass() == v.getClass()) {
            return internalCompareTo(v);
        }
        return order() - v.order();
    }

    default int internalCompareTo(Value v) {
        return 0;
    }

    default boolean isConstant() {
        return false;
    }

    default boolean isUnknown() {
        return false;
    }

    default boolean hasConstantProperties() {
        return true;
    }

    // executed without context, default for all constant types
    default int getPropertyOutsideContext(VariableProperty variableProperty) {
        if (VariableProperty.DYNAMIC_TYPE_PROPERTY.contains(variableProperty)) return variableProperty.best;
        if (VariableProperty.NOT_NULL == variableProperty) return Level.TRUE; // constants are not null
        if (VariableProperty.FIELD_AND_METHOD_PROPERTIES.contains(variableProperty)) return Level.DELAY;

        throw new UnsupportedOperationException("No info about " + variableProperty + " for value " + getClass());
    }

    default int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return getPropertyOutsideContext(variableProperty);
    }

    default IntValue toInt() {
        throw new UnsupportedOperationException(this.getClass().toString());
    }

    @NotModified
    default Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        return Set.of();
    }

    /**
     * @return a map with all clauses, true for V == null, false for V != null
     */
    default Map<Variable, Boolean> individualNullClauses() {
        return Map.of();
    }

    /**
     * @return the type, if we are certain; used in WidestType for operators
     */
    default ParameterizedType type() {
        return null;
    }

    // HELPERS, NO NEED TO IMPLEMENT

    default boolean isNotNull0(EvaluationContext evaluationContext) {
        return Level.value(getProperty(evaluationContext, VariableProperty.NOT_NULL), Level.NOT_NULL) == Level.TRUE;
    }

    default Set<Variable> variables() {
        return Set.of();
    }

    default boolean isNotNotNull0(EvaluationContext evaluationContext) {
        return Level.value(getProperty(evaluationContext, VariableProperty.NOT_NULL), Level.NOT_NULL) == Level.FALSE;
    }

    default Map<Variable, Value> individualSizeRestrictions() {
        return Map.of();
    }

    default int sizeRestriction() {
        return Level.FALSE;
    }
}
