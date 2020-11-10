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

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;

import java.util.Objects;

public class StringValue extends ConstantValue implements Constant<String> {
    public final String value;
    private final ParameterizedType stringParameterizedType;

    public StringValue(Primitives primitives, String value) {
        this(primitives, value, ObjectFlow.NO_FLOW);
    }

    public StringValue(Primitives primitives, String value, ObjectFlow objectFlow) {
        super(objectFlow);
        this.value = Objects.requireNonNull(value);
        this.stringParameterizedType = primitives.stringParameterizedType;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        if (VariableProperty.SIZE == variableProperty) {
            return Level.encodeSizeEquals(value.length());
        }
        if (VariableProperty.SIZE_COPY == variableProperty) {
            return Level.FALSE;
        }
        return super.getProperty(evaluationContext, variableProperty);
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public int order() {
        return ORDER_CONSTANT_STRING;
    }

    @Override
    public int internalCompareTo(Value v) {
        return value.compareTo(((StringValue) v).value);
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StringValue that = (StringValue) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public ParameterizedType type() {
        return stringParameterizedType;
    }
}
