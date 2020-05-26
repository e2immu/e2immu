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

    /**
     * if xNegated is false: -b + x >= 0 or x >= b
     * if xNegated is true: b - x >= 0 or x <= b
     */
    public static class XB {
        public final Value x;
        public final double b;
        public final boolean lessThan; // if true, >= becomes <=

        public XB(Value x, double b, boolean lessThan) {
            this.x = x;
            this.b = b;
            this.lessThan = lessThan;
        }
    }

    public XB extract() {
        if (value instanceof SumValue) {
            SumValue sumValue = (SumValue) value;
            if (sumValue.lhs instanceof NumericValue) {
                Value v = sumValue.rhs;
                Value x;
                boolean lessThan;
                double b;
                if (v instanceof NegatedValue) {
                    x = ((NegatedValue) v).value;
                    lessThan = true;
                    b = ((NumericValue) sumValue.lhs).getNumber().doubleValue();
                } else {
                    x = v;
                    lessThan = false;
                    b = ((NumericValue) NegatedValue.negate(sumValue.lhs, false)).getNumber().doubleValue();
                }
                return new XB(x, b, lessThan);
            }
        }
        Value x;
        boolean lessThan;
        if (value instanceof NegatedValue) {
            x = ((NegatedValue) value).value;
            lessThan = true;
        } else {
            x = value;
            lessThan = false;
        }
        return new XB(x, 0.0d, lessThan);
    }

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
        return new GreaterThanZeroValue(SumValue.sum(l, NegatedValue.negate(r, false)), allowEquals);
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
    public int order() {
        return ORDER_GEQ0;
    }

    @Override
    public int internalCompareTo(Value v) {
        return value.compareTo(((GreaterThanZeroValue) v).value);
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
