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
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class EqualsValue extends PrimitiveValue {
    public final Value lhs;
    public final Value rhs;

    public EqualsValue(Value lhs, Value rhs) {
        boolean swap = lhs.compareTo(rhs) > 0;
        this.lhs = swap ? rhs : lhs;
        this.rhs = swap ? lhs : rhs;
    }

    public static Value equals(Value l, Value r) {
        if (l.equals(r)) return BoolValue.TRUE;
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
        if (l instanceof ConstrainedNumericValue && ((ConstrainedNumericValue) l).rejects(r)) return BoolValue.FALSE;
        if (r instanceof ConstrainedNumericValue && ((ConstrainedNumericValue) r).rejects(l)) return BoolValue.FALSE;
        return new EqualsValue(l, r);
    }

    @Override
    public String toString() {
        return lhs + " == " + rhs;
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof EqualsValue) {
            int c = lhs.compareTo(((EqualsValue) o).lhs);
            if (c == 0) {
                c = rhs.compareTo(((EqualsValue) o).rhs);
            }
            return c;
        }
        return -1;
    }

    @Override
    public Map<Variable, Boolean> individualNullClauses() {
        return lhs instanceof NullValue && rhs instanceof VariableValue ? Map.of(((VariableValue) rhs).variable, true) :
                Map.of();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EqualsValue that = (EqualsValue) o;
        return lhs.equals(that.lhs) &&
                rhs.equals(that.rhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs);
    }


    @Override
    public ParameterizedType type() {
        return Primitives.PRIMITIVES.booleanParameterizedType;
    }


    @Override
    public Set<Variable> variables() {
        return SetUtil.immutableUnion(lhs.variables(), rhs.variables());
    }
}
