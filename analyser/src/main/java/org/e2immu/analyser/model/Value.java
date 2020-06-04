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
    int ORDER_PRODUCT = 41;
    int ORDER_DIVIDE = 42;
    int ORDER_REMAINDER = 43;
    int ORDER_SUM = 44;
    int ORDER_BITWISE_AND = 45;

    // variables, types
    int ORDER_PRIMITIVE = 60;
    int ORDER_ARRAY = 61;
    int ORDER_CONSTRAINED_NUMERIC_VALUE = 62;
    int ORDER_INSTANCE = 63;
    int ORDER_METHOD = 64;
    int ORDER_VARIABLE_VALUE = 65;
    int ORDER_PARAMETER = 66;
    int ORDER_COMBINED = 67;
    int ORDER_TYPE = 68;
    int ORDER_NO_VALUE = 69;
    int ORDER_CONDITIONAL = 70;

    // boolean operations
    int ORDER_INSTANCE_OF = 81;
    int ORDER_EQUALS = 82;
    int ORDER_GEQ0 = 83;
    int ORDER_NEGATED = 84;
    int ORDER_OR = 85;
    int ORDER_AND = 86;

    int order();

    @Override
    default int compareTo(Value v) {
        // negations are always AFTER their argument
        if (v instanceof NegatedValue && this.equals(((NegatedValue) v).value)) {
            return -1;
        }
        if (this instanceof NegatedValue && v.equals(((NegatedValue) this).value)) {
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

    default boolean isDiscreteType() {
        ParameterizedType type = type();
        return type != null && type.isDiscrete();
    }

    // executed without context, default for all constant types
    default int getPropertyOutsideContext(VariableProperty variableProperty) {
        if (VariableProperty.DYNAMIC_TYPE_PROPERTY.contains(variableProperty)) return variableProperty.best;
        if (VariableProperty.NOT_NULL == variableProperty) return Level.TRUE; // constants are not null
        if (VariableProperty.FIELD_AND_METHOD_PROPERTIES.contains(variableProperty)) return Level.DELAY;

        throw new UnsupportedOperationException("No info about " + variableProperty + " for value " + getClass());
    }

    // only called from EvaluationContext.getProperty().
    // Use that method as the general way of obtaining a value for a property from a Value object
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

    default Set<Variable> variables() {
        return Set.of();
    }

    default Map<Variable, Value> individualSizeRestrictions() {
        return Map.of();
    }

    default int encodedSizeRestriction() {
        return Level.FALSE;
    }
}
