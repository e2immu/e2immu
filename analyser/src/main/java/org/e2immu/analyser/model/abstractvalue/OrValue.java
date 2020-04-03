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
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Primitives;

import java.util.Objects;

public class OrValue implements Value {
    public final Value lhs;
    public final Value rhs;

    public OrValue(Value lhs, Value rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    // we try to maintain a CNF
    public static Value or(Value l, Value r) {
        if (l.equals(r)) return l; // A || A == A, !A || !A == !A

        // the following 2 if statements allow for a reduction to TRUE if l or r is unknown, variable, etc.
        if (l instanceof BoolValue && ((BoolValue) l).value) return BoolValue.TRUE;
        if (r instanceof BoolValue && ((BoolValue) r).value) return BoolValue.TRUE;

        // normal: 2x bool
        if (l instanceof BoolValue && r instanceof BoolValue) {
            return ((BoolValue) l).value || ((BoolValue) r).value ? BoolValue.TRUE : BoolValue.FALSE;
        }
        // any unknown lingering
        if (l == UnknownValue.UNKNOWN_VALUE || r == UnknownValue.UNKNOWN_VALUE)
            return new Instance(Primitives.PRIMITIVES.booleanParameterizedType);

        if (l instanceof NegatedValue && ((NegatedValue) l).value.equals(r)) return BoolValue.TRUE; // !A || A
        if (r instanceof NegatedValue && ((NegatedValue) r).value.equals(l)) return BoolValue.TRUE; // A || !A

        if (r instanceof AndValue) {
            AndValue and = (AndValue) r;
            return AndValue.and(or(l, and.lhs), or(l, and.rhs));
        }
        if (l instanceof AndValue) {
            AndValue and = (AndValue) l;
            return AndValue.and(or(and.lhs, r), or(and.rhs, r));
        }
        return l.compareTo(r) < 0 ? new OrValue(l, r) : new OrValue(r, l);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrValue orValue = (OrValue) o;
        return lhs.equals(orValue.lhs) &&
                rhs.equals(orValue.rhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs);
    }

    @Override
    public String toString() {
        return "(" + lhs + " or " + rhs + ")";
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof OrValue) {
            // comparing 2 or values...compare lhs
            int c = lhs.compareTo(((OrValue) o).lhs);
            if (c == 0) {
                c = rhs.compareTo(((OrValue) o).rhs);
            }
            return c;
        }
        if (o instanceof UnknownValue) return -1;
        return 1; // go to the back
    }

    @Override
    public Boolean isNotNull(EvaluationContext evaluationContext) {
        return true;
    }
}
