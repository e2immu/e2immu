package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.analyser.parser.Primitives;

public class ConstrainedNumericValue extends PrimitiveValue {
    public static final double MIN = -Double.MAX_VALUE;
    public static final double MAX = Double.MAX_VALUE;

    public final double upperBound;
    public final double lowerBound;
    public final boolean allowEquals;
    public final ParameterizedType type;

    public static ConstrainedNumericValue lowerBound(ParameterizedType type, double value, boolean allowEquals) {
        return new ConstrainedNumericValue(type, value, MAX, allowEquals);
    }

    public static ConstrainedNumericValue upperBound(ParameterizedType type, double value, boolean allowEquals) {
        return new ConstrainedNumericValue(type, MIN, value, allowEquals);
    }

    public ConstrainedNumericValue(ParameterizedType type, double lowerBound, double upperBound, boolean allowEquals) {
        this.upperBound = upperBound;
        this.lowerBound = lowerBound;
        assert upperBound < MAX || lowerBound > MIN;
        this.allowEquals = allowEquals;
        this.type = type;
    }

    @Override
    public int compareTo(Value o) {
        return 0;
    }

    @Override
    public String asString() {
        if (upperBound == MAX) {
            return allowEquals ? "?>=" + lowerBound : "?>" + lowerBound;
        }
        if (lowerBound == MIN) {
            return allowEquals ? "?<=" + upperBound : "?<" + upperBound;
        }
        return allowEquals ? lowerBound + "<=?<=" + upperBound : lowerBound + "<?<" + upperBound;
    }

    public boolean rejects(Value r) {
        if (r instanceof NumericValue) {
            double number = ((NumericValue) r).getNumber().doubleValue();
            if (allowEquals)
                return number < lowerBound || number > upperBound;
            return number <= lowerBound || number >= upperBound;
        }
        return false;
    }

    public boolean rejectsGreaterThanZero(boolean allowEquals) {
        if (allowEquals) {
            return upperBound < 0;
        }
        return upperBound <= 0;
    }

    public boolean guaranteesGreaterThanZero(boolean allowEquals) {
        if (allowEquals) {
            return lowerBound >= 0;
        }
        return lowerBound > 0;
    }

    @Override
    public ParameterizedType type() {
        return type;
    }

    // if lb <= x <= ub, what is -x?
    // -ub <= x <= -lb
    public ConstrainedNumericValue negatedValue() {
        return new ConstrainedNumericValue(type, -upperBound, -lowerBound, allowEquals);
    }

    public Value sum(Number number) {
        return new ConstrainedNumericValue(type,
                boundedSum(lowerBound, number.doubleValue()),
                boundedSum(upperBound, number.doubleValue()), allowEquals);
    }

    public Value sum(ConstrainedNumericValue v) {
        return new ConstrainedNumericValue(Primitives.PRIMITIVES.widestType(type, v.type),
                boundedSum(lowerBound, v.lowerBound),
                boundedSum(upperBound, v.upperBound), allowEquals);
    }

    public Value product(ConstrainedNumericValue v) {
        return new ConstrainedNumericValue(Primitives.PRIMITIVES.widestType(type, v.type),
                boundedProduct(lowerBound, v.lowerBound),
                boundedProduct(upperBound, v.upperBound), allowEquals);
    }

    public Value divide(ConstrainedNumericValue v) {
        return new ConstrainedNumericValue(Primitives.PRIMITIVES.widestType(type, v.type),
                boundedDivide(lowerBound, v.lowerBound),
                boundedDivide(upperBound, v.upperBound), allowEquals);
    }

    public Value product(Number number) {
        return new ConstrainedNumericValue(type,
                boundedProduct(lowerBound, number.doubleValue()),
                boundedProduct(upperBound, number.doubleValue()), allowEquals);
    }

    public Value divide(Number number) {
        return new ConstrainedNumericValue(type,
                boundedDivide(lowerBound, number.doubleValue()),
                boundedDivide(upperBound, number.doubleValue()), allowEquals);
    }

    private static double boundedSum(double x, double y) {
        if (x == MIN || x == MAX) return x;
        if (y == MIN || y == MAX) return y;
        return x + y;
    }

    private static double boundedProduct(double x, double y) {
        if (x == MIN || x == MAX) return x;
        if (y == MIN || y == MAX) return y;
        return x * y;
    }

    private static double boundedDivide(double x, double y) {
        if (x == MIN || x == MAX) return x;
        if (y == MIN || y == MAX) return y;
        return x / y;
    }
}
