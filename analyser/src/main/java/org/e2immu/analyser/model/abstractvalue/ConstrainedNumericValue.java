package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class ConstrainedNumericValue extends PrimitiveValue implements ValueWrapper {
    public static final double MIN = -Double.MAX_VALUE;
    public static final double MAX = Double.MAX_VALUE;

    public final double upperBound;
    public final double lowerBound;
    public final Value value;
    public final boolean integer;

    @Override
    public boolean isNumeric() {
        return true;
    }

    @Override
    public Value getValue() {
        return value;
    }

    @Override
    public int wrapperOrder() {
        return WRAPPER_ORDER_CONSTRAINED_NUMERIC_VALUE;
    }

    public static ConstrainedNumericValue lowerBound(EvaluationContext evaluationContext, Value value, double lowerBound) {
        Primitives primitives = evaluationContext.getAnalyserContext().getPrimitives();
        if (value instanceof ConstrainedNumericValue cnv) {
            if (cnv.lowerBound >= lowerBound) return cnv; // nothing to do!
            return new ConstrainedNumericValue(primitives, cnv.value, lowerBound, cnv.upperBound);
        }
        return new ConstrainedNumericValue(primitives, value, lowerBound, MAX);
    }

    public static ConstrainedNumericValue upperBound(EvaluationContext evaluationContext, Value value, double upperBound) {
        Primitives primitives = evaluationContext.getAnalyserContext().getPrimitives();

        if (value instanceof ConstrainedNumericValue cnv) {
            if (cnv.upperBound <= upperBound) return cnv; // nothing to do!
            return new ConstrainedNumericValue(primitives, cnv.value, cnv.lowerBound, upperBound);
        }
        return new ConstrainedNumericValue(primitives, value, MIN, upperBound);
    }

    public ConstrainedNumericValue(Primitives primitives, Value value, double lowerBound, double upperBound) {
        super(value.getObjectFlow());
        if (value instanceof ConstrainedNumericValue) throw new UnsupportedOperationException();

        this.upperBound = upperBound;
        this.lowerBound = lowerBound;
        assert upperBound >= lowerBound;
        this.value = value;
        ParameterizedType type = value.type();
        this.integer = type.typeInfo != primitives.floatTypeInfo && type.typeInfo != primitives.doubleTypeInfo;
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        EvaluationResult re = value.reEvaluate(evaluationContext, translation);
        if (re.value.isConstant()) return re;
        return new EvaluationResult.Builder().compose(re).setValue(ConstrainedNumericValue.create(evaluationContext,
                re.value, lowerBound, upperBound)).build();
    }

    private static Value create(EvaluationContext evaluationContext, Value value, double lowerBound, double upperBound) {
        if (upperBound == MAX) {
            return lowerBound(evaluationContext, value, lowerBound);
        }
        if (lowerBound == MIN) {
            return upperBound(evaluationContext, value, upperBound);
        }
        Primitives primitives = evaluationContext.getAnalyserContext().getPrimitives();
        if (value instanceof ConstrainedNumericValue cnv) {
            // save ourselves a new object....
            if (cnv.lowerBound >= lowerBound && cnv.upperBound <= upperBound) return value;
            return new ConstrainedNumericValue(primitives, value, Math.max(lowerBound, cnv.lowerBound), Math.min(upperBound, cnv.upperBound));
        }
        return new ConstrainedNumericValue(primitives, value, lowerBound, upperBound);
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

    public boolean onlyLowerBound() {
        return lowerBound != MIN && upperBound == MAX;
    }

    @Override
    public ParameterizedType type() {
        return value.type();
    }

    private static boolean equals(double x, double y) {
        return Math.abs(x - y) < 0.0000001;
    }

    @Override
    public int encodedSizeRestriction(EvaluationContext evaluationContext) {
        if (!integer) throw new UnsupportedOperationException();

        if (lowerBound == upperBound) {
            return Level.encodeSizeEquals((int) lowerBound);
        }
        if (lowerBound > 0) {
            return Level.encodeSizeMin((int) lowerBound);
        }
        return Level.FALSE; // no decent value
    }

    /*
    situation: NOT(n == CNV), as in for example, NOT(0 == v,?>=0).

    0 == v,?>=0 is not something that can be resolved. But the boolean complement can be simplified to 0 < v,?>=0
     */

    public Value notEquals(EvaluationContext evaluationContext, NumericValue numericValue) {
        double x = numericValue.getNumber().doubleValue();

        if (lowerBound == upperBound) {
            throw new UnsupportedOperationException(); // should not get here, the equality would have been simplified
        }
        if (equals(x, lowerBound)) {
            // not(x == v,?>=x) transforms to v,?>=x > x
            return GreaterThanZeroValue.greater(evaluationContext, this, numericValue, false, getObjectFlow());
        }
        if (equals(x, upperBound)) {
            // not(x == v,?<=x) transforms to v,?<=x < x
            return GreaterThanZeroValue.less(evaluationContext, this, numericValue, false, getObjectFlow());
        }
        return null;
    }

    @Override
    public Set<Variable> variables() {
        return value.variables();
    }

    @Override
    public void visit(Consumer<Value> consumer) {
        value.visit(consumer);
        consumer.accept(this);
    }
}
