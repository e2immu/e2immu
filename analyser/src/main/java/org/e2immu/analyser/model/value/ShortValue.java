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

import org.e2immu.analyser.model.Constant;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.parser.Primitives;

import java.util.Objects;

public class ShortValue implements Value, Constant<Short>, NumericValue {
    public final short value;

    public ShortValue(short value) {
        this.value = value;
    }

    @Override
    public NumericValue negate() {
        return new IntValue(-value);
    }

    @Override
    public IntValue toInt() {
        return new IntValue((int) value);
    }

    @Override
    public String toString() {
        return value < 0 ? "(" + value + ")" : Short.toString(value);
    }

    @Override
    public String asString() {
        return Short.toString(value);
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof IntValue) return value - ((IntValue) o).value;
        if (o instanceof ByteValue) return value - ((ByteValue) o).value;
        if (o instanceof ShortValue) return value - ((ShortValue) o).value;
        return -1; // I'm on the left
    }

    @Override
    public Short getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShortValue that = (ShortValue) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public ParameterizedType type() {
        return Primitives.PRIMITIVES.shortParameterizedType;
    }
}
