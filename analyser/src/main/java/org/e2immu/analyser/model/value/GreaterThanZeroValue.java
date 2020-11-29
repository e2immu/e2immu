/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class GreaterThanZeroValue extends PrimitiveValue {
    public final Value value;
    public final boolean allowEquals;
    private final ParameterizedType booleanParameterizedType;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GreaterThanZeroValue that = (GreaterThanZeroValue) o;
        if (allowEquals != that.allowEquals) return false;
        return value.equals(that.value);
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        EvaluationResult reValue = value.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reValue);
        return builder.setValue(GreaterThanZeroValue.greater(evaluationContext,
                reValue.value,
                new IntValue(evaluationContext.getPrimitives(), 0, ObjectFlow.NO_FLOW),
                allowEquals, getObjectFlow())).build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, allowEquals);
    }

    // NOT (x >= 0) == x < 0  == (not x) > 0
    // NOT (x > 0)  == x <= 0 == (not x) >= 0
    // note that this one does not solve the int-problem where we always want to maintain allowEquals == True
    public Value negate(EvaluationContext evaluationContext) {
        if (value instanceof SumValue sumValue) {
            if (sumValue.lhs instanceof NumericValue && sumValue.lhs.isDiscreteType()) {
                // NOT (-3 + x >= 0) == NOT (x >= 3) == x < 3 == x <= 2 == 2 + -x >= 0
                // NOT (3 + x >= 0) == NOT (x >= -3) == x < -3 == x <= -4 == -4 + -x >= 0
                Value minusSumPlusOne = NumericValue.intOrDouble(evaluationContext.getPrimitives(),
                        -(((NumericValue) sumValue.lhs).getNumber().doubleValue() + 1.0),
                        sumValue.lhs.getObjectFlow());
                return new GreaterThanZeroValue(booleanParameterizedType,
                        SumValue.sum(evaluationContext, minusSumPlusOne,
                                NegatedValue.negate(evaluationContext, sumValue.rhs), value.getObjectFlow()), true, getObjectFlow());
            }
        }
        return new GreaterThanZeroValue(booleanParameterizedType, NegatedValue.negate(evaluationContext, value), !allowEquals, getObjectFlow());
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

    public XB extract(EvaluationContext evaluationContext) {
        if (value instanceof SumValue sumValue) {
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
                    b = ((NumericValue) NegatedValue.negate(evaluationContext, sumValue.lhs)).getNumber().doubleValue();
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

    private GreaterThanZeroValue(ParameterizedType booleanParameterizedType, Value value, boolean allowEquals, ObjectFlow objectFlow) {
        super(objectFlow);
        this.value = value;
        this.allowEquals = allowEquals;
        this.booleanParameterizedType = booleanParameterizedType;
    }

    // testing only
    public static Value greater(EvaluationContext evaluationContext, Value l, Value r, boolean allowEquals) {
        return greater(evaluationContext, l, r, allowEquals, ObjectFlow.NO_FLOW);
    }

    public static Value greater(EvaluationContext evaluationContext, Value l, Value r, boolean allowEquals, ObjectFlow objectFlow) {
        Primitives primitives = evaluationContext.getPrimitives();
        if (l.equals(r) && !allowEquals) return BoolValue.createFalse(primitives);
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;


        if (l instanceof NumericValue && r instanceof NumericValue) {
            if (allowEquals)
                return new BoolValue(primitives, l.toInt().value >= r.toInt().value, objectFlow);
            return new BoolValue(primitives, l.toInt().value > r.toInt().value, objectFlow);
        }

        ParameterizedType intParameterizedType = evaluationContext.getPrimitives().intParameterizedType;
        ParameterizedType booleanParameterizedType = evaluationContext.getPrimitives().booleanParameterizedType;

        ObjectFlow objectFlowSum = objectFlow == null ? null : new ObjectFlow(objectFlow.location, intParameterizedType, Origin.RESULT_OF_OPERATOR);

        if (l instanceof NumericValue && !allowEquals && l.isDiscreteType()) {
            // 3 > x == 3 + (-x) > 0 transform to 2 >= x
            Value lMinusOne = NumericValue.intOrDouble(primitives, ((NumericValue) l).getNumber().doubleValue() - 1.0, l.getObjectFlow());
            return new GreaterThanZeroValue(booleanParameterizedType,
                    SumValue.sum(evaluationContext, lMinusOne,
                            NegatedValue.negate(evaluationContext, r),
                            objectFlowSum), true, objectFlow);
        }
        if (r instanceof NumericValue && !allowEquals && r.isDiscreteType()) {
            // x > 3 == -3 + x > 0 transform to x >= 4
            Value minusRPlusOne = NumericValue.intOrDouble(primitives, -(((NumericValue) r).getNumber().doubleValue() + 1.0), r.getObjectFlow());
            return new GreaterThanZeroValue(booleanParameterizedType,
                    SumValue.sum(evaluationContext, l, minusRPlusOne, objectFlowSum), true, objectFlow);
        }

        return new GreaterThanZeroValue(booleanParameterizedType,
                SumValue.sum(evaluationContext, l, NegatedValue.negate(evaluationContext, r), objectFlowSum), allowEquals, objectFlow);
    }

    // testing only
    public static Value less(EvaluationContext evaluationContext, Value l, Value r, boolean allowEquals) {
        return less(evaluationContext, l, r, allowEquals, ObjectFlow.NO_FLOW);
    }

    public static Value less(EvaluationContext evaluationContext, Value l, Value r, boolean allowEquals, ObjectFlow objectFlow) {
        Primitives primitives = evaluationContext.getPrimitives();
        if (l.equals(r) && !allowEquals) return BoolValue.createFalse(primitives);
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;


        if (l instanceof NumericValue && r instanceof NumericValue) {
            if (allowEquals)
                return new BoolValue(primitives, l.toInt().value <= r.toInt().value, objectFlow);
            return new BoolValue(primitives, l.toInt().value < r.toInt().value, objectFlow);
        }

        ParameterizedType intParameterizedType = evaluationContext.getPrimitives().intParameterizedType;
        ParameterizedType booleanParameterizedType = evaluationContext.getPrimitives().booleanParameterizedType;

        ObjectFlow objectFlowSum = objectFlow == null ? null : new ObjectFlow(objectFlow.location, intParameterizedType, Origin.RESULT_OF_OPERATOR);

        if (l instanceof NumericValue && !allowEquals && l.isDiscreteType()) {
            // 3 < x == x > 3 == -3 + x > 0 transform to x >= 4
            Value minusLPlusOne = NumericValue.intOrDouble(primitives, -(((NumericValue) l).getNumber().doubleValue() + 1.0), l.getObjectFlow());
            return new GreaterThanZeroValue(booleanParameterizedType,
                    SumValue.sum(evaluationContext, minusLPlusOne, r, objectFlowSum), true, objectFlow);
        }
        if (r instanceof NumericValue && !allowEquals && r.isDiscreteType()) {
            // x < 3 == 3 + -x > 0 transform to x <= 2 == 2 + -x >= 0
            Value rMinusOne = NumericValue.intOrDouble(primitives, ((NumericValue) r).getNumber().doubleValue() - 1.0, r.getObjectFlow());
            return new GreaterThanZeroValue(booleanParameterizedType,
                    SumValue.sum(evaluationContext, NegatedValue.negate(evaluationContext, l), rMinusOne, objectFlowSum), true, objectFlow);
        }
        // l < r <=> l-r < 0 <=> -l+r > 0
        if (l instanceof NumericValue) {
            return new GreaterThanZeroValue(booleanParameterizedType,
                    SumValue.sum(evaluationContext, ((NumericValue) l).negate(), r, objectFlowSum), allowEquals, objectFlow);
        }

        // TODO add tautology call

        return new GreaterThanZeroValue(primitives.booleanParameterizedType, SumValue.sum(evaluationContext,
                NegatedValue.negate(evaluationContext, l), r, objectFlowSum), allowEquals, objectFlow);
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        if (printMode.forDebug()) {
            String op = allowEquals ? ">=" : ">";
            return value + " " + op + " 0";
        }
        // transparent
        return value.print(printMode);
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
        return booleanParameterizedType;
    }

    @Override
    public Set<Variable> variables() {
        return value.variables();
    }

    @Override
    public void visit(Predicate<Value> predicate) {
        if(predicate.test(this)) {
            value.visit(predicate);
        }
    }
}