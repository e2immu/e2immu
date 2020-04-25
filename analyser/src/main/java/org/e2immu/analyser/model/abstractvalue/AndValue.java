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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

// TODO NOTE that importantly, the reshuffling here ignores the short-circuiting!

public class AndValue implements Value {
    public final Value lhs;
    public final Value rhs;

    public AndValue(Value lhs, Value rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    // we try to maintain a CNF
    public static Value and(Value l, Value r) {
        // some trivial reductions
        if (l.equals(r)) return l; // A && A == A, !A && !A == !A

        // the following 2 if statements allow for a reduction to FALSE
        if (l instanceof BoolValue && !((BoolValue) l).value) return BoolValue.FALSE;
        if (r instanceof BoolValue && !((BoolValue) r).value) return BoolValue.FALSE;

        // normal: 2x bool
        if (l instanceof BoolValue && r instanceof BoolValue) {
            return ((BoolValue) l).value && ((BoolValue) r).value ? BoolValue.TRUE : BoolValue.FALSE;
        }
        // unknown value lingering
        if (l == UnknownValue.UNKNOWN_VALUE || r == UnknownValue.UNKNOWN_VALUE)
            return new Instance(Primitives.PRIMITIVES.booleanParameterizedType);

        if (l instanceof NegatedValue && ((NegatedValue) l).value.equals(r)) return BoolValue.FALSE; // !A && A
        if (r instanceof NegatedValue && ((NegatedValue) r).value.equals(l)) return BoolValue.FALSE; // A && !A

        // TODO more complicated evaluation...
        
        // otherwise... do sorting
        if (l instanceof AndValue && r instanceof AndValue) {
            // TODO make this recursive, for multiple AND combinations
            AndValue ab = (AndValue) l;
            AndValue cd = (AndValue) r;
            ArrayList<Value> list = new ArrayList<>(4);
            Collections.addAll(list, ab.lhs, ab.rhs, cd.lhs, cd.rhs);
            Collections.sort(list);
            return new AndValue(new AndValue(list.get(0), list.get(1)), new AndValue(list.get(2), list.get(3)));
        }
        return l.compareTo(r) < 0 ? new AndValue(l, r) : new AndValue(r, l);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AndValue andValue = (AndValue) o;
        return lhs.equals(andValue.lhs) &&
                rhs.equals(andValue.rhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs);
    }

    @Override
    public String toString() {
        return "(" + lhs + " and " + rhs + ")";
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof AndValue) {
            int c = lhs.compareTo(((AndValue) o).lhs);
            if (c == 0) {
                c = rhs.compareTo(((AndValue) o).rhs);
            }
            return c;
        }
        if (o instanceof UnknownValue) return -1;
        return 1;
    }

    @Override
    public Boolean isNotNull(EvaluationContext evaluationContext) {
        return true;
    }
}
