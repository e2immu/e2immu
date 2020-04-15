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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NullNotAllowed;

import java.util.List;
import java.util.Objects;

public class ClazzValue implements Value, Constant<ParameterizedType> {
    public final ParameterizedType value;

    public ClazzValue(@NullNotAllowed ParameterizedType parameterizedType) {
        this.value = Objects.requireNonNull(parameterizedType);
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public String asString() {
        return value.toString();
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof ClazzValue) {
            return value.toString().compareTo(((ClazzValue) o).value.toString());
        }
        return -1; // I'm on the left
    }

    @Override
    public ParameterizedType getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClazzValue that = (ClazzValue) o;
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
}
