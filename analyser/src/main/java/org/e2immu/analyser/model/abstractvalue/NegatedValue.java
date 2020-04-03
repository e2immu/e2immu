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

package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Primitives;

import java.util.Objects;

public class NegatedValue implements Value {
    public static NegatedValue NOT_NULL = new NegatedValue(NullValue.NULL_VALUE);

    public final Value value;

    public NegatedValue(Value value) {
        this.value = value;
    }

    public static Value negate(Value v) {
        if (v instanceof BoolValue) {
            BoolValue boolValue = (BoolValue) v;
            return boolValue.value ? BoolValue.FALSE : BoolValue.TRUE;
        }
        if (v instanceof NumericValue) {
            return ((NumericValue) v).negate();
        }
        if (v == UnknownValue.UNKNOWN_VALUE)
            return OrValue.or(new Instance(Primitives.PRIMITIVES.intParameterizedType),
                    new Instance(Primitives.PRIMITIVES.booleanParameterizedType));

        if (v instanceof NegatedValue) return ((NegatedValue) v).value;
        if (v instanceof OrValue) {
            OrValue or = (OrValue) v;
            return new AndValue(negate(or.lhs), negate(or.rhs));
        }
        if (v instanceof AndValue) {
            AndValue and = (AndValue) v;
            return new OrValue(negate(and.lhs), negate(and.rhs));
        }
        return new NegatedValue(v);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NegatedValue that = (NegatedValue) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        if (value instanceof EqualsValue) {
            return ((EqualsValue) value).lhs + " != " + ((EqualsValue) value).rhs;
        }
        return "not " + value;
    }

    @Override
    public int compareTo(Value o) {
        return value.compareTo(o);
    }

    @Override
    public Boolean isNotNull(EvaluationContext evaluationContext) {
        return true;
    }
}
