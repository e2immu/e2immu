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
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.abstractvalue.NegatedValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.ValueComparator;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

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
    int ORDER_INLINE_METHOD = 64;
    int ORDER_METHOD = 65;
    int ORDER_VARIABLE_VALUE = 66;
    int ORDER_COMBINED = 67;
    int ORDER_TYPE = 68;
    int ORDER_NO_VALUE = 69;
    int ORDER_CONDITIONAL = 70;
    int ORDER_SWITCH = 71;

    // boolean operations
    int ORDER_INSTANCE_OF = 81;
    int ORDER_EQUALS = 82;
    int ORDER_GEQ0 = 83;
    int ORDER_OR = 85;
    int ORDER_AND = 86;

    int order();

    default boolean isNumeric() {
        return false;
    }

    @Override
    default int compareTo(Value v) {
        return ValueComparator.SINGLETON.compare(this, v);
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
        return type != null && Primitives.isDiscrete(type);
    }

    // only called from EvaluationContext.getProperty().
    // Use that method as the general way of obtaining a value for a property from a Value object
    // do NOT fall back on evaluationContext.getProperty(this, ...) because that'll be an infinite loop!

    default int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        throw new UnsupportedOperationException("For type " + getClass() + ", property " + variableProperty);
    }

    default IntValue toInt() {
        throw new UnsupportedOperationException(this.getClass().toString());
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

    default FilterResult isIndividualNullOrNotNullClauseOnParameter() {
        return new FilterResult(Map.of(), this);
    }
    // NOTE: contrary to the FieldCondition, this one stores not the whole equality, but
    // only the NullValue in the Map.

    default FilterResult isIndividualNullOrNotNullClause() {
        return new FilterResult(Map.of(), this);
    }

    default FilterResult isIndividualFieldCondition() {
        Set<Variable> variables = variables();
        if (variables.size() == 1 && variables.stream().allMatch(v -> v instanceof FieldReference)) {
            return new FilterResult(Map.of(variables.stream().findAny().orElseThrow(), this), UnknownValue.NO_VALUE);
        }
        return new FilterResult(Map.of(), this);
    }

    default boolean isNotNull() {
        NegatedValue negatedValue = asInstanceOf(NegatedValue.class);
        return negatedValue != null && negatedValue.value.isInstanceOf(NullValue.class);
    }

    default boolean isNull() {
        return isInstanceOf(NullValue.class);
    }

    default boolean isComputeProperties() {
        return this != UnknownValue.RETURN_VALUE;
    }

    default boolean isBoolValueTrue() {
        BoolValue boolValue;
        return ((boolValue = this.asInstanceOf(BoolValue.class)) != null) && boolValue.value;
    }

    default boolean isBoolValueFalse() {
        BoolValue boolValue;
        return ((boolValue = this.asInstanceOf(BoolValue.class)) != null) && !boolValue.value;
    }

    default Stream<Value> individualBooleanClauses(FilterMode filterMode) {
        return Stream.empty();
    }

    Instance getInstance(EvaluationContext evaluationContext);

    class FilterResult {
        public final Map<Variable, Value> accepted;
        public final Value rest;

        public FilterResult(Map<Variable, Value> accepted, Value rest) {
            this.accepted = accepted;
            this.rest = rest;
        }
    }

    @FunctionalInterface
    interface FilterMethod {
        FilterResult apply(Value value);
    }

    enum FilterMode {
        ALL,
        ACCEPT, // normal state of the variable AFTER the escape; independent = AND; does not recurse into OrValues
        REJECT, // condition for escaping; independent = OR; does not recurse into AndValues
    }

    /**
     * Filtering in the components of a value; implemented for AndValue, OrValue, NegatedValue, EqualsValue.
     * The FilterResult contains individual clauses in a map accepted by the filter methods, and the rest of the value.
     *
     * @param filterMode    mode for filtering
     * @param filterMethods if multiple accepted, the map contains the first result. (It should contain an AND, but see null clause)
     * @return a FilterResult object, always, if only NO_RESULT
     */
    default FilterResult filter(EvaluationContext evaluationContext, FilterMode filterMode, FilterMethod... filterMethods) {
        return filterMethods[0].apply(this);
    }

    /**
     * @return the type, if we are certain; used in WidestType for operators
     */
    default ParameterizedType type() {
        return null;
    }

    default Set<Variable> variables() {
        return Set.of();
    }

    default int encodedSizeRestriction(EvaluationContext evaluationContext) {
        return Level.FALSE;
    }

    default EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        Value inMap = translation.get(this);
        return new EvaluationResult.Builder().setValue(inMap == null ? this : inMap).build();
    }

    ObjectFlow getObjectFlow();

    default void visit(Consumer<Value> consumer) {
        consumer.accept(this);
    }

    default boolean isInstanceOf(Class<? extends Value> clazz) {
        return clazz.isAssignableFrom(getClass());
    }

    default <T extends Value> T asInstanceOf(Class<T> clazz) {
        if (clazz.isAssignableFrom(getClass())) {
            return (T) this;
        }
        return null;
    }

    default String print(PrintMode printMode) {
        return toString();
    }
}
