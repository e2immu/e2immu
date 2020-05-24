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

import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.analyser.parser.Primitives;

import java.util.Set;

public class GreaterThanZeroValue extends PrimitiveValue {
    public final Value value;
    public final boolean allowEquals;

    public GreaterThanZeroValue(Value value, boolean allowEquals) {
        this.value = value;
        this.allowEquals = allowEquals;
    }

    public static Value greater(Value l, Value r, boolean allowEquals) {
        if (l.equals(r) && !allowEquals) return BoolValue.FALSE;
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;

        if (l instanceof NumericValue && r instanceof NumericValue) {
            if (allowEquals)
                return BoolValue.of(l.toInt().value >= r.toInt().value);
            return BoolValue.of(l.toInt().value > r.toInt().value);
        }
        if (r instanceof NumericValue) {
            return new GreaterThanZeroValue(SumValue.sum(((NumericValue) r).negate(), l), allowEquals);
        }
        if (r instanceof ConstrainedNumericValue) {
            ConstrainedNumericValue cnv = (ConstrainedNumericValue) r;
            if (cnv.rejectsGreaterThanZero(allowEquals)) {
                return BoolValue.FALSE;
            }
            if (cnv.guaranteesGreaterThanZero(allowEquals)) {
                return BoolValue.TRUE;
            }
        }
        return new GreaterThanZeroValue(SumValue.sum(l, NegatedValue.negate(r, true)), allowEquals);
    }

    public static Value less(Value l, Value r, boolean allowEquals) {
        if (l.equals(r) && !allowEquals) return BoolValue.FALSE;
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
        if (l instanceof NumericValue && r instanceof NumericValue) {
            if (allowEquals)
                return BoolValue.of(l.toInt().value <= r.toInt().value);
            return BoolValue.of(l.toInt().value < r.toInt().value);
        }
        // l < r <=> l-r < 0 <=> -l+r > 0
        if (l instanceof NumericValue) {
            return new GreaterThanZeroValue(SumValue.sum(((NumericValue) l).negate(), r), allowEquals);
        }
        return new GreaterThanZeroValue(SumValue.sum(NegatedValue.negate(l, true), r), allowEquals);
    }

    @Override
    public String toString() {
        String op = allowEquals ? ">=" : ">";
        return value + " " + op + " 0";
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof GreaterThanZeroValue) {
            return value.compareTo(((GreaterThanZeroValue) o).value);
        }
        return -1;
    }

    @Override
    public ParameterizedType type() {
        return Primitives.PRIMITIVES.booleanParameterizedType;
    }

    @Override
    public Set<Variable> variables() {
        return value.variables();
    }
}
