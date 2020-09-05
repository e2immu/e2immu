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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class GreaterThanZeroValue extends PrimitiveValue {
    public final Value value;
    public final boolean allowEquals;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GreaterThanZeroValue that = (GreaterThanZeroValue) o;
        if (allowEquals != that.allowEquals) return false;
        if (value.equals(that.value)) return true;
        // there's another situation that we want to be true
        return specialEquals(value, that.value);
    }

    private static boolean specialEquals(Value v1, Value v2) {
        CNVGreaterThan cnv1 = isCnv(v1);
        if (cnv1 == null || !cnv1.cnv.onlyLowerBound()) return false;
        CNVGreaterThan cnv2 = isCnv(v2);
        if (cnv2 == null || !cnv2.cnv.onlyLowerBound()) return false;
        cnv1.checkOnlyLowerBound();
        cnv2.checkOnlyLowerBound();

        // if cnv,?>=x >= y && cnv,?>=x' >= y',
        // and y == y',
        // then both expressions are equal, regardless of the value of x or x'

        return cnv1.nv == cnv2.nv;
    }

    private static CNVGreaterThan isCnv(Value value) {
        if (value instanceof ConstrainedNumericValue) {
            return new CNVGreaterThan((ConstrainedNumericValue) value, 0.0);
        }
        if (value instanceof SumValue) {
            SumValue sumValue = (SumValue) value;
            if (sumValue.lhs instanceof NumericValue && sumValue.rhs instanceof ConstrainedNumericValue) {
                ConstrainedNumericValue cnv = (ConstrainedNumericValue) sumValue.rhs;
                double v = ((NumericValue) sumValue.lhs).getNumber().doubleValue();
                return new CNVGreaterThan(cnv, -v);
            }
        }
        return null;
    }

    static class CNVGreaterThan {
        final ConstrainedNumericValue cnv;
        final double nv;

        CNVGreaterThan(ConstrainedNumericValue cnv, double nv) {
            this.cnv = cnv;
            this.nv = nv;
        }

        void checkOnlyLowerBound() {
            // cnv,?>=lb >= nv is the situation
            // now if lb >= nv then we should not have this value, we simply should have cnv
            // (it would be saying: cnv,?>=1 >= 1, which is a tautology
            if (cnv.lowerBound >= nv)
                throw new UnsupportedOperationException("Have LB " + cnv.lowerBound + ", >= " + nv);
        }
    }

    @Override
    public Value reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        Value reValue = value.reEvaluate(evaluationContext, translation);
        return GreaterThanZeroValue.greater(reValue, IntValue.ZERO_VALUE, allowEquals, getObjectFlow());
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
                Value minusSumPlusOne = NumericValue.intOrDouble(-(((NumericValue) sumValue.lhs).getNumber().doubleValue() + 1.0),
                        sumValue.lhs.getObjectFlow());
                return new GreaterThanZeroValue(SumValue.sum(minusSumPlusOne, NegatedValue.negate(sumValue.rhs), value.getObjectFlow()), true, getObjectFlow());
            }
        }
        return new GreaterThanZeroValue(NegatedValue.negate(value), !allowEquals, getObjectFlow());
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

    public GreaterThanZeroValue(Value value, boolean allowEquals, ObjectFlow objectFlow) {
        super(objectFlow);
        this.value = value;
        this.allowEquals = allowEquals;
    }

    // testing only
    public static Value greater(Value l, Value r, boolean allowEquals) {
        return greater(l, r, allowEquals, ObjectFlow.NO_FLOW);
    }

    public static Value greater(Value l, Value r, boolean allowEquals, ObjectFlow objectFlow) {
        if (l.equals(r) && !allowEquals) return BoolValue.FALSE;
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;

        if (l instanceof NumericValue && r instanceof NumericValue) {
            if (allowEquals)
                return new BoolValue(l.toInt().value >= r.toInt().value, objectFlow);
            return new BoolValue(l.toInt().value > r.toInt().value, objectFlow);
        }

        Value v = tautologyGreaterThan(l, r, allowEquals);
        if (v != null) return v;

        ObjectFlow objectFlowSum = objectFlow == null ? null : new ObjectFlow(objectFlow.location, Primitives.PRIMITIVES.intParameterizedType, Origin.RESULT_OF_OPERATOR);

        if (l instanceof NumericValue && !allowEquals && l.isDiscreteType()) {
            // 3 > x == 3 + (-x) > 0 transform to 2 >= x
            Value lMinusOne = NumericValue.intOrDouble(((NumericValue) l).getNumber().doubleValue() - 1.0, l.getObjectFlow());
            return new GreaterThanZeroValue(SumValue.sum(lMinusOne, NegatedValue.negate(r), objectFlowSum), true, objectFlow);
        }
        if (r instanceof NumericValue && !allowEquals && r.isDiscreteType()) {
            // x > 3 == -3 + x > 0 transform to x >= 4
            Value minusRPlusOne = NumericValue.intOrDouble(-(((NumericValue) r).getNumber().doubleValue() + 1.0), r.getObjectFlow());
            return new GreaterThanZeroValue(SumValue.sum(l, minusRPlusOne, objectFlowSum), true, objectFlow);
        }

        return new GreaterThanZeroValue(SumValue.sum(l, NegatedValue.negate(r), objectFlowSum), allowEquals, objectFlow);
    }

    // check ConstrainedNV
    private static Value tautologyGreaterThan(Value l, Value r, boolean allowEquals) {
        if (l instanceof ConstrainedNumericValue && r instanceof NumericValue) {
            ConstrainedNumericValue cnv = (ConstrainedNumericValue) l;
            double v = ((NumericValue) r).getNumber().doubleValue();
            // cnv,?>= v, >= v (trivial)   and   > v (real restriction)
            if (v == cnv.lowerBound) return allowEquals ? BoolValue.TRUE : null;

            // cnv,?>=x >= v, v<x
            if (cnv.onlyLowerBound() && v < cnv.lowerBound) return BoolValue.TRUE;
        }
        return null;
    }

    // testing only
    public static Value less(Value l, Value r, boolean allowEquals) {
        return less(l, r, allowEquals, ObjectFlow.NO_FLOW);
    }

    public static Value less(Value l, Value r, boolean allowEquals, ObjectFlow objectFlow) {
        if (l.equals(r) && !allowEquals) return BoolValue.FALSE;
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;

        if (l instanceof NumericValue && r instanceof NumericValue) {
            if (allowEquals)
                return new BoolValue(l.toInt().value <= r.toInt().value, objectFlow);
            return new BoolValue(l.toInt().value < r.toInt().value, objectFlow);
        }

        ObjectFlow objectFlowSum = objectFlow == null ? null : new ObjectFlow(objectFlow.location, Primitives.PRIMITIVES.intParameterizedType, Origin.RESULT_OF_OPERATOR);

        if (l instanceof NumericValue && !allowEquals && l.isDiscreteType()) {
            // 3 < x == x > 3 == -3 + x > 0 transform to x >= 4
            Value minusLPlusOne = NumericValue.intOrDouble(-(((NumericValue) l).getNumber().doubleValue() + 1.0), l.getObjectFlow());
            return new GreaterThanZeroValue(SumValue.sum(minusLPlusOne, r, objectFlowSum), true, objectFlow);
        }
        if (r instanceof NumericValue && !allowEquals && r.isDiscreteType()) {
            // x < 3 == 3 + -x > 0 transform to x <= 2 == 2 + -x >= 0
            Value rMinusOne = NumericValue.intOrDouble(((NumericValue) r).getNumber().doubleValue() - 1.0, r.getObjectFlow());
            return new GreaterThanZeroValue(SumValue.sum(NegatedValue.negate(l), rMinusOne, objectFlowSum), true, objectFlow);
        }
        // l < r <=> l-r < 0 <=> -l+r > 0
        if (l instanceof NumericValue) {
            return new GreaterThanZeroValue(SumValue.sum(((NumericValue) l).negate(), r, objectFlowSum), allowEquals, objectFlow);
        }

        // TODO add tautology call

        return new GreaterThanZeroValue(SumValue.sum(NegatedValue.negate(l), r, objectFlowSum), allowEquals, objectFlow);
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
            return Level.encodeSizeMin((int) xb.b);
        }
        return 0;
    }

    @Override
    public FilterResult isIndividualSizeRestrictionOnParameter() {
        XB xb = extract();
        if (!xb.lessThan && xb.x instanceof ConstrainedNumericValue) {
            ConstrainedNumericValue cnv = (ConstrainedNumericValue) xb.x;
            if (cnv.value instanceof MethodValue) {
                MethodValue methodValue = (MethodValue) cnv.value;
                if (methodValue.methodInfo.typeInfo.sizeMethod() == methodValue.methodInfo) {
                    // I am the size method!
                    if (methodValue.object instanceof ValueWithVariable) {
                        ValueWithVariable v = (ValueWithVariable) methodValue.object;
                        return new FilterResult(Map.of(v.variable, this), UnknownValue.NO_VALUE);
                    }
                }
            }
        }
        return new FilterResult(Map.of(), this);
    }

    @Override
    public void visit(Consumer<Value> consumer) {
        value.visit(consumer);
        consumer.accept(this);
    }
}
