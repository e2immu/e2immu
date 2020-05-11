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

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.abstractvalue.EqualsValue;
import org.e2immu.analyser.model.abstractvalue.NegatedValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.NotModified;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Shared properties: @NotNull(n), dynamic type properties (@Immutable(n), @Container)
 * Properties of variables are ALWAYS computed inside an evaluation context; properties of methods come from outside the scope only.
 */
public interface Value extends Comparable<Value> {

    // executed without context
    default int getPropertyOutsideContext(VariableProperty variableProperty) {
        if (VariableProperty.NOT_NULL == variableProperty) return Level.TRUE; // default = @NotNull
        throw new UnsupportedOperationException("No info about " + variableProperty);
    }

    default int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        if (VariableProperty.NOT_NULL == variableProperty) return Level.TRUE; // default = @NotNull
        throw new UnsupportedOperationException("No info about " + variableProperty);
    }

    default IntValue toInt() {
        throw new UnsupportedOperationException(this.getClass().toString());
    }

    default String asString() {
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

    default int isNotNull0OutsideContext() {
        return Level.value(getPropertyOutsideContext(VariableProperty.NOT_NULL), Level.NOT_NULL);
    }

    default Set<Variable> variables() {
        return Set.of();
    }
}
