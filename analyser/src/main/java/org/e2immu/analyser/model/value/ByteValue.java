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
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;

import java.util.Objects;

public class ByteValue extends ConstantValue implements Constant<Byte>, NumericValue {
    public final byte value;
    private final Primitives primitives;

    public ByteValue(Primitives primitives, byte value, ObjectFlow objectFlow) {
        super(objectFlow);
        this.value = value;
        this.primitives = primitives;
    }

    @Override
    public int order() {
        return ORDER_CONSTANT_BYTE;
    }

    @Override
    public int internalCompareTo(Value v) {
        return value - ((ByteValue) v).value;
    }

    @Override
    public NumericValue negate() {
        return new IntValue(primitives, -value, getObjectFlow());
    }

    @Override
    public IntValue toInt() {
        return new IntValue(primitives, value, getObjectFlow());
    }

    @Override
    public Number getNumber() {
        return value;
    }

    @Override
    public String toString() {
        return value < 0 ? "(" + value + ")" : Byte.toString(value);
    }

    @Override
    public Byte getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ByteValue byteValue = (ByteValue) o;
        return value == byteValue.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public ParameterizedType type() {
        return primitives.byteParameterizedType;
    }
}
