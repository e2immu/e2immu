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
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.parser.Primitives;

import java.util.Objects;

public class StringValue implements Value, Constant<String> {
    public final String value;

    public StringValue(String value) {
        this.value = Objects.requireNonNull(value);
    }

    public static Value concat(Value l, Value r) {
        if (accept(l) && accept(r)) {
            return new StringValue(l.asString() + r.asString());
        }
        return new Instance(Primitives.PRIMITIVES.stringParameterizedType);
    }

    private static boolean accept(Value v) {
        return v instanceof StringValue || v instanceof NumericValue || v instanceof NullValue || v instanceof ClassValue;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public String asString() {
        return value;
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof StringValue) {
            return value.compareTo(((StringValue) o).value);
        }
        return -1; // I'm on the left
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
    public Boolean isNotNull(EvaluationContext evaluationContext) {
        return true;
    }


    @Override
    public ParameterizedType type() {
        return Primitives.PRIMITIVES.stringParameterizedType;
    }
}
