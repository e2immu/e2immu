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
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setValue(EqualsValue.equals(reLhs.value, reRhs.value, objectFlow, evaluationContext)).build();
    }

    public static Value equals(Value l, Value r, ObjectFlow objectFlow, EvaluationContext evaluationContext) {
        if (l.equals(r)) return BoolValue.TRUE;

        if (l instanceof NullValue && isNotNull0(r, evaluationContext)) return BoolValue.FALSE;
        if (r instanceof NullValue && isNotNull0(l, evaluationContext)) return BoolValue.FALSE;

        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
        if (l instanceof ConstrainedNumericValue && ((ConstrainedNumericValue) l).rejects(r)) return BoolValue.FALSE;
        if (r instanceof ConstrainedNumericValue && ((ConstrainedNumericValue) r).rejects(l)) return BoolValue.FALSE;

        return new EqualsValue(l, r, objectFlow);
    }

    private static boolean isNotNull0(Value value, EvaluationContext evaluationContext) {
        if (evaluationContext == null) {
            return MultiLevel.isEffectivelyNotNull(value.getPropertyOutsideContext(VariableProperty.NOT_NULL));
        }
        return evaluationContext.isNotNull0(value);
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
                    if (!parametersOnly || variableValue.variable instanceof ParameterInfo) {
                        return new FilterResult(Map.of(variableValue.variable, this), UnknownValue.EMPTY);
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
    public FilterResult isIndividualSizeRestriction() {
        return isIndividualSizeRestriction(false);
    }

    private FilterResult isIndividualNotNullClause(boolean parametersOnly) {
        if (lhs instanceof NullValue && rhs instanceof VariableValue) {
            VariableValue v = (VariableValue) rhs;
            if (!parametersOnly || v.variable instanceof ParameterInfo) {
                return new FilterResult(Map.of(v.variable, lhs), UnknownValue.EMPTY);
            }
        }
        return new FilterResult(Map.of(), this);
    }

    @Override
    public FilterResult isIndividualNotNullClauseOnParameter() {
        return isIndividualNotNullClause(true);
    }

    @Override
    public FilterResult isIndividualNotNullClause() {
        return isIndividualNotNullClause(false);
    }

    @Override
    public FilterResult isIndividualFieldCondition() {
        boolean acceptR = rhs instanceof VariableValue && ((VariableValue) rhs).variable instanceof FieldReference;
        boolean acceptL = lhs instanceof VariableValue && ((VariableValue) lhs).variable instanceof FieldReference;
        if (acceptL && !acceptR)
            return new FilterResult(Map.of(((VariableValue) lhs).variable, this), UnknownValue.EMPTY);
        if (acceptR && !acceptL)
            return new FilterResult(Map.of(((VariableValue) rhs).variable, this), UnknownValue.EMPTY);
        return new FilterResult(Map.of(), this);
    }

    @Override
    public FilterResult filter(FilterMode filterMode, FilterMethod... filterMethods) {
        for (FilterMethod filterMethod : filterMethods) {
            FilterResult filterResult = filterMethod.apply(this);
            if (!filterResult.accepted.isEmpty()) return filterResult;
        }
        return new FilterResult(Map.of(), this);
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
    public void visit(Consumer<Value> consumer) {
        lhs.visit(consumer);
        rhs.visit(consumer);
        consumer.accept(this);
    }
}
