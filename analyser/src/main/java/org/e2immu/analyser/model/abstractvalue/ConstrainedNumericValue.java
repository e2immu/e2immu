package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.Analysis;
import org.e2immu.analyser.model.Level;
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
    public final boolean integer;

    public static ConstrainedNumericValue equalTo(ParameterizedType type, double value) {
        return new ConstrainedNumericValue(type, value, value, true);
    }

    public static ConstrainedNumericValue intLowerBound(double value, boolean allowEquals) {
        return new ConstrainedNumericValue(Primitives.PRIMITIVES.intParameterizedType, value, MAX, allowEquals);
    }

    public static ConstrainedNumericValue lowerBound(ParameterizedType type, double value, boolean allowEquals) {
        return new ConstrainedNumericValue(type, value, MAX, allowEquals);
    }

    public static ConstrainedNumericValue upperBound(ParameterizedType type, double value, boolean allowEquals) {
        return new ConstrainedNumericValue(type, MIN, value, allowEquals);
    }

    public ConstrainedNumericValue(ParameterizedType type, double lowerBound, double upperBound, boolean allowEquals) {
        this.upperBound = upperBound;
        this.lowerBound = lowerBound;
        assert upperBound >= lowerBound;
        this.allowEquals = allowEquals;
        this.type = type;
        this.integer = type.typeInfo != Primitives.PRIMITIVES.floatTypeInfo && type.typeInfo != Primitives.PRIMITIVES.doubleTypeInfo;
    }

    @Override
    public int order() {
        return ORDER_CONSTRAINED_NUMERIC_VALUE;
    }

    @Override
    public String toString() {
        if (upperBound == MAX) {
            String lb = nice(lowerBound);
            return allowEquals ? "?>=" + lb : "?>" + lb;
        }
        if (lowerBound == MIN) {
            String ub = nice(upperBound);
            return allowEquals ? "?<=" + ub : "?<" + ub;
        }
        String lb = nice(lowerBound);
        String ub = nice(upperBound);
        return allowEquals ? lb + "<=?<=" + ub : lb + "<?<" + ub;
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
    public ConstrainedNumericValue numericNegatedValue() {
        return new ConstrainedNumericValue(type, -upperBound, -lowerBound, allowEquals);
    }

    public Value booleanNegatedValue(boolean valuesOfProperties) {
        if (valuesOfProperties) {
            // there is always, implicitly or explicitly, a lower bound of 0
            // the type is always integer
            assert type.typeInfo == Primitives.PRIMITIVES.intTypeInfo;
            if (lowerBound > 0) {
                return new ConstrainedNumericValue(type, allowEquals ? -1 : 0, lowerBound, !allowEquals);
            }
            return lowerBound(type, upperBound, !allowEquals);
        }
        if (upperBound == MAX && lowerBound == MIN) {
            // nothing allowed
            return new ConstrainedNumericValue(type, 0.0, 0.0, false);
        }
        if (upperBound == lowerBound) {
            // everything allowed
            return lowerBound(type, MIN, allowEquals);
        }
        if (upperBound != MAX && lowerBound != MIN) {
            // combination
            return new AndValue().append(lowerBound(type, lowerBound, allowEquals).booleanNegatedValue(false),
                    upperBound(type, upperBound, allowEquals).booleanNegatedValue(false));
        }
        if (upperBound != MAX) return ConstrainedNumericValue.lowerBound(type, upperBound, !allowEquals);
        return upperBound(type, lowerBound, !allowEquals);
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

    private static boolean equals(double x, double y) {
        return Math.abs(x - y) < 0.0000001;
    }

    @Override
    public int sizeRestriction() {
        if (!integer) throw new UnsupportedOperationException();
        if (allowEquals) {
            if (equals(lowerBound, upperBound)) {
                return Analysis.encodeSizeEquals((int) lowerBound);
            }
            if (lowerBound > 0 && upperBound == MAX) {
                return Analysis.encodeSizeMin((int) lowerBound);
            }
            return Level.FALSE; // no decent value
        }
        boolean equals = lowerBound + 2 == upperBound;
        if (equals) {
            return Analysis.encodeSizeEquals((int) lowerBound + 1);
        }
        if (lowerBound >= 0 && upperBound == MAX) {
            return Analysis.encodeSizeMin((int) lowerBound + 1);
        }
        return Level.FALSE;
    }

    /*
    situation: NOT(x == CNV), as in for example, NOT(0 == ?>=0).

    0 == ?>=0 is not something that can be resolved. But the boolean complement can be resolved to ?>0
     */

    public Value notEquals(NumericValue numericValue) {
        double x = numericValue.getNumber().doubleValue();
        if (allowEquals) {
            if (equals(x, lowerBound)) {
                // not(x == ?>=x) -> ?>x
                return lowerBound(type, lowerBound, false);
            }
            if (equals(x, upperBound)) {
                // not(x == ?<=x) -> ?<x
                return upperBound(type, upperBound, false);
            }
            return null;
        }
        if (integer) {
            if (equals(x, lowerBound + 1)) {
                // not(x+1 == ?>x)) ->  ?>x+1  (not (3 == ?>2) -> ?>3)
                return lowerBound(type, lowerBound + 1, false);
            }
            if (equals(x, upperBound - 1)) {
                // not(x-1 == ?<x) -> ?<x-1
                return upperBound(type, upperBound - 1, false);
            }
        }
        return null;
    }
}
