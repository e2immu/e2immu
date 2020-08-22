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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class EqualsValue extends PrimitiveValue {
    public final Value lhs;
    public final Value rhs;

    // testing only
    public EqualsValue(Value lhs, Value rhs) {
        this(lhs, rhs, ObjectFlow.NO_FLOW);
    }

    public EqualsValue(Value lhs, Value rhs, ObjectFlow objectFlow) {
        super(objectFlow);
        boolean swap = lhs.compareTo(rhs) > 0;
        this.lhs = swap ? rhs : lhs;
        this.rhs = swap ? lhs : rhs;
    }

    @Override
    public Value reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        Value reLhs = lhs.reEvaluate(evaluationContext, translation);
        Value reRhs = rhs.reEvaluate(evaluationContext, translation);
        return EqualsValue.equals(reLhs, reRhs, objectFlow);
    }

    // testing only
    public static Value equals(Value l, Value r) {
        return equals(l, r, ObjectFlow.NO_FLOW);
    }

    public static Value equals(Value l, Value r, ObjectFlow objectFlow) {
        if (l.equals(r)) return BoolValue.TRUE;

        if (l instanceof NullValue && MultiLevel.isEffectivelyNotNull(r.getPropertyOutsideContext(VariableProperty.NOT_NULL)))
            return BoolValue.FALSE;
        if (r instanceof NullValue && MultiLevel.isEffectivelyNotNull(l.getPropertyOutsideContext(VariableProperty.NOT_NULL)))
            return BoolValue.FALSE;

        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
        if (l instanceof ConstrainedNumericValue && ((ConstrainedNumericValue) l).rejects(r)) return BoolValue.FALSE;
        if (r instanceof ConstrainedNumericValue && ((ConstrainedNumericValue) r).rejects(l)) return BoolValue.FALSE;


        return new EqualsValue(l, r, objectFlow);
    }

    @Override
    public String toString() {
        return lhs + " == " + rhs;
    }

    @Override
    public int order() {
        return ORDER_EQUALS;
    }

    @Override
    public int internalCompareTo(Value v) {
        int c = lhs.compareTo(((EqualsValue) v).lhs);
        if (c == 0) {
            c = rhs.compareTo(((EqualsValue) v).rhs);
        }
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EqualsValue that = (EqualsValue) o;
        return lhs.equals(that.lhs) &&
                rhs.equals(that.rhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs);
    }


    @Override
    public ParameterizedType type() {
        return Primitives.PRIMITIVES.booleanParameterizedType;
    }


    @Override
    public Set<Variable> variables() {
        return SetUtil.immutableUnion(lhs.variables(), rhs.variables());
    }

    private FilterResult isIndividualSizeRestriction(boolean parametersOnly) {
        // constants always left, methods always right;
        // methods for size should be wrapped with a ConstrainedNumericValue
        if (lhs instanceof NumericValue && rhs instanceof ConstrainedNumericValue && ((ConstrainedNumericValue) rhs).value instanceof MethodValue) {
            MethodValue methodValue = (MethodValue) ((ConstrainedNumericValue) rhs).value;
            if (methodValue.methodInfo.typeInfo.hasSize()) {
                int sizeOnMethod = methodValue.methodInfo.methodAnalysis.get().getProperty(VariableProperty.SIZE);
                if (sizeOnMethod >= Level.TRUE && methodValue.object instanceof VariableValue) {
                    VariableValue variableValue = (VariableValue) methodValue.object;
                    if(!parametersOnly || variableValue.variable instanceof ParameterInfo) {
                        return new FilterResult(Map.of(variableValue.variable, this), UnknownValue.NO_VALUE);
                    }
                }
            }
        }
        return new FilterResult(Map.of(), this);
    }
    @Override
    public FilterResult isIndividualSizeRestrictionOnParameter() {
        return isIndividualSizeRestriction(true);
    }

    @Override
    public FilterResult isIndividualNotNullClauseOnParameter() {
        if (lhs instanceof NullValue && rhs instanceof ValueWithVariable) {
            ValueWithVariable v = (ValueWithVariable) rhs;
            if (v.variable instanceof ParameterInfo) {
                return new FilterResult(Map.of(v.variable, lhs), UnknownValue.NO_VALUE);
            }
        }
        return new FilterResult(Map.of(), this);
    }

    @Override
    public FilterResult isIndividualNotNullClause() {
        if (lhs instanceof NullValue && rhs instanceof ValueWithVariable) {
            ValueWithVariable v = (ValueWithVariable) rhs;
            return new FilterResult(Map.of(v.variable, lhs), UnknownValue.NO_VALUE);
        }
        return new FilterResult(Map.of(), this);
    }

    @Override
    public FilterResult isIndividualFieldCondition() {
        boolean acceptR = rhs instanceof ValueWithVariable && ((ValueWithVariable) rhs).variable instanceof FieldReference;
        boolean acceptL = lhs instanceof ValueWithVariable && ((ValueWithVariable) lhs).variable instanceof FieldReference;
        if (acceptL && !acceptR)
            return new FilterResult(Map.of(((ValueWithVariable) lhs).variable, rhs), UnknownValue.NO_VALUE);
        if (acceptR && !acceptL)
            return new FilterResult(Map.of(((ValueWithVariable) rhs).variable, lhs), UnknownValue.NO_VALUE);
        return new FilterResult(Map.of(), this);
    }


    @Override
    public FilterResult filter(boolean preconditionSide, Function<Value, FilterResult> filterMethod) {
        return filterMethod.apply(this);
    }

    @Override
    public int encodedSizeRestriction() {
        if (lhs instanceof NumericValue && lhs.isDiscreteType()) {
            int size = ((NumericValue) lhs).getNumber().intValue();
            return Level.encodeSizeEquals(size);
        }
        return 0;
    }

    @Override
    public boolean isExpressionOfParameters() {
        return lhs.isExpressionOfParameters() && rhs.isExpressionOfParameters();
    }

    @Override
    public void visit(Consumer<Value> consumer) {
        lhs.visit(consumer);
        rhs.visit(consumer);
        consumer.accept(this);
    }
}
