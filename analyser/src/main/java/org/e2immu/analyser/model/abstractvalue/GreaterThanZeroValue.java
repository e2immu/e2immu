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

import org.e2immu.analyser.model.Analysis;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.analyser.parser.Primitives;

import java.util.Objects;
import java.util.Set;

public class GreaterThanZeroValue extends PrimitiveValue {
    public final Value value;
    public final boolean allowEquals;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GreaterThanZeroValue that = (GreaterThanZeroValue) o;
        return allowEquals == that.allowEquals &&
                value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, allowEquals);
    }

    // NOT (x >= 0) == x < 0  == (not x) > 0
    // NOT (x > 0)  == x <= 0 == (not x) >= 0
    // note that this one does not solve the int-problem where we always want to maintain allowEquals == True
    public Value negate() {
        if (value instanceof SumValue) {
            SumValue sumValue = (SumValue) value;
            if (sumValue.lhs instanceof NumericValue && sumValue.lhs.isDiscreteType()) {
                // NOT (-3 + x >= 0) == NOT (x >= 3) == x < 3 == x <= 2 == 2 + -x >= 0
                // NOT (3 + x >= 0) == NOT (x >= -3) == x < -3 == x <= -4 == -4 + -x >= 0
                Value minusSumPlusOne = NumericValue.intOrDouble(-(((NumericValue) sumValue.lhs).getNumber().doubleValue() + 1.0));
                return new GreaterThanZeroValue(SumValue.sum(minusSumPlusOne, NegatedValue.negate(sumValue.rhs)), true);
            }
        }
        return new GreaterThanZeroValue(NegatedValue.negate(value), !allowEquals);
    }

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
                    b = ((NumericValue) NegatedValue.negate(sumValue.lhs)).getNumber().doubleValue();
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
        if (l instanceof NumericValue && !allowEquals && l.isDiscreteType()) {
            // 3 > x == 3 + (-x) > 0 transform to 2 >= x
            Value lMinusOne = NumericValue.intOrDouble(((NumericValue) l).getNumber().doubleValue() - 1.0);
            return new GreaterThanZeroValue(SumValue.sum(lMinusOne, NegatedValue.negate(r)), true);
        }
        if (r instanceof NumericValue && !allowEquals && r.isDiscreteType()) {
            // x > 3 == -3 + x > 0 transform to x >= 4
            Value minusRPlusOne = NumericValue.intOrDouble(-(((NumericValue) r).getNumber().doubleValue() + 1.0));
            return new GreaterThanZeroValue(SumValue.sum(l, minusRPlusOne), true);
        }
        return new GreaterThanZeroValue(SumValue.sum(l, NegatedValue.negate(r)), allowEquals);
    }

    public static Value less(Value l, Value r, boolean allowEquals) {
        if (l.equals(r) && !allowEquals) return BoolValue.FALSE;
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
        if (l instanceof NumericValue && r instanceof NumericValue) {
            if (allowEquals)
                return BoolValue.of(l.toInt().value <= r.toInt().value);
            return BoolValue.of(l.toInt().value < r.toInt().value);
        }
        if (l instanceof NumericValue && !allowEquals && l.isDiscreteType()) {
            // 3 < x == x > 3 == -3 + x > 0 transform to x >= 4
            Value minusLPlusOne = NumericValue.intOrDouble(-(((NumericValue) l).getNumber().doubleValue() + 1.0));
            return new GreaterThanZeroValue(SumValue.sum(minusLPlusOne, r), true);
        }
        if (r instanceof NumericValue && !allowEquals && r.isDiscreteType()) {
            // x < 3 == 3 + -x > 0 transform to x <= 2 == 2 + -x >= 0
            Value rMinusOne = NumericValue.intOrDouble(((NumericValue) r).getNumber().doubleValue() - 1.0);
            return new GreaterThanZeroValue(SumValue.sum(NegatedValue.negate(l), rMinusOne), true);
        }
        // l < r <=> l-r < 0 <=> -l+r > 0
        if (l instanceof NumericValue) {
            return new GreaterThanZeroValue(SumValue.sum(((NumericValue) l).negate(), r), allowEquals);
        }
        return new GreaterThanZeroValue(SumValue.sum(NegatedValue.negate(l), r), allowEquals);
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

    @Override
    public int encodedSizeRestriction() {
        XB xb = extract();
        if (!xb.lessThan) {
            return Analysis.encodeSizeMin((int) xb.b);
        }
        return 0;
    }

    @Override
    public boolean isExpressionOfParameters() {
        return value.isExpressionOfParameters();
    }
}
