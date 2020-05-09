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

public class DoubleValue implements Constant<Double>, NumericValue {
    public final double value;

    public DoubleValue(double value) {
        this.value = value;
    }

    @Override
    public NumericValue negate() {
        return new DoubleValue(-value);
    }

    @Override
    public IntValue toInt() {
        throw new UnsupportedOperationException("Cannot cast from double to int without loss");
    }

    @Override
    public String toString() {
        return value < 0 ? "(" + value + ")" : Double.toString(value);
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof DoubleValue) return Double.compare(value, ((DoubleValue) o).value);
        if (o instanceof LongValue) return Double.compare(value, ((LongValue) o).value);
        if (o instanceof IntValue) return Double.compare(value, ((IntValue) o).value);
        if (o instanceof ByteValue) return Double.compare(value, ((ByteValue) o).value);
        if (o instanceof ShortValue) return Double.compare(value, ((ShortValue) o).value);
        return -1; // I'm on the left
    }

    @Override
    public String asString() {
        return Double.toString(value);
    }

    @Override
    public Double getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DoubleValue intValue = (DoubleValue) o;
        return value == intValue.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public ParameterizedType type() {
        return Primitives.PRIMITIVES.doubleParameterizedType;
    }
}
