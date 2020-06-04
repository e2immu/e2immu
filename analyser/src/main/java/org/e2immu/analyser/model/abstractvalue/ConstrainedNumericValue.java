package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Analysis;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.analyser.parser.Primitives;

import java.util.Objects;

public class ConstrainedNumericValue extends PrimitiveValue {
    public static final double MIN = -Double.MAX_VALUE;
    public static final double MAX = Double.MAX_VALUE;

    public final double upperBound;
    public final double lowerBound;
    public final Value value;
    public final boolean integer;

    public static ConstrainedNumericValue equalTo(Value value, double upperAndLower) {
        return new ConstrainedNumericValue(value, upperAndLower, upperAndLower);
    }

    public static ConstrainedNumericValue lowerBound(Value value, double lowerBound) {
        return new ConstrainedNumericValue(value, lowerBound, MAX);
    }

    public static ConstrainedNumericValue upperBound(Value value, double upperBound) {
        return new ConstrainedNumericValue(value, MIN, upperBound);
    }

    public ConstrainedNumericValue(Value value, double lowerBound, double upperBound) {
        this.upperBound = upperBound;
        this.lowerBound = lowerBound;
        assert upperBound >= lowerBound;
        this.value = value;
        ParameterizedType type = value.type();
        this.integer = type.typeInfo != Primitives.PRIMITIVES.floatTypeInfo && type.typeInfo != Primitives.PRIMITIVES.doubleTypeInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConstrainedNumericValue that = (ConstrainedNumericValue) o;
        return Double.compare(that.upperBound, upperBound) == 0 &&
                Double.compare(that.lowerBound, lowerBound) == 0 &&
                integer == that.integer &&
                value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(upperBound, lowerBound, value, integer);
    }

    @Override
    public int order() {
        return ORDER_CONSTRAINED_NUMERIC_VALUE;
    }

    @Override
    public String toString() {
        String bound;
        if (upperBound == MAX) {
            String lb = nice(lowerBound);
            bound = "?>=" + lb;
        } else if (lowerBound == MIN) {
            String ub = nice(upperBound);
            bound = "?<=" + ub;
        } else if (upperBound == lowerBound) {
            String b = nice(lowerBound);
            bound = "?=" + b;
        } else {
            String lb = nice(lowerBound);
            String ub = nice(upperBound);
            bound = lb + "<=?<=" + ub;
        }
        return value.toString() + "," + bound;
    }

    private String nice(double v) {
        if (integer) {
            return Long.toString((long) v);
        }
        return Double.toString(v);
    }

    public boolean rejects(Value r) {
        if (r instanceof NumericValue) {
            double number = ((NumericValue) r).getNumber().doubleValue();
            return number < lowerBound || number > upperBound;
        }
        return false;
    }

    @Override
    public ParameterizedType type() {
        return value.type();
    }

    private static boolean equals(double x, double y) {
        return Math.abs(x - y) < 0.0000001;
    }

    @Override
    public int encodedSizeRestriction() {
        if (!integer) throw new UnsupportedOperationException();

        if (lowerBound == upperBound) {
            return Analysis.encodeSizeEquals((int) lowerBound);
        }
        if (lowerBound > 0) {
            return Analysis.encodeSizeMin((int) lowerBound);
        }
        return Level.FALSE; // no decent value
    }

    /*
    situation: NOT(n == CNV), as in for example, NOT(0 == v,?>=0).

    0 == v,?>=0 is not something that can be resolved. But the boolean complement can be simplified to 0 < v,?>=0
     */

    public Value notEquals(NumericValue numericValue) {
        double x = numericValue.getNumber().doubleValue();

        if (lowerBound == upperBound) {
            throw new UnsupportedOperationException(); // should not get here, the equality would have been simplified
        }
        if (equals(x, lowerBound)) {
            // not(x == v,?>=x) transforms to v,?>=x > x
            return GreaterThanZeroValue.greater(this, numericValue, false);
        }
        if (equals(x, upperBound)) {
            // not(x == v,?<=x) transforms to v,?<=x < x
            return GreaterThanZeroValue.less(this, numericValue, false);
        }
        return null;
    }

    @Override
    public boolean isExpressionOfParameters() {
        return value.isExpressionOfParameters();
    }
}
