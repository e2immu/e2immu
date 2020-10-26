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
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class NegatedValue extends PrimitiveValue implements ValueWrapper {
    public static NegatedValue NOT_NULL = new NegatedValue(NullValue.NULL_VALUE);

    public final Value value;

    public Value getValue() {
        return value;
    }

    @Override
    public int wrapperOrder() {
        return WRAPPER_ORDER_NEGATED;
    }

    private NegatedValue(@NotNull Value value) {
        super(value.getObjectFlow());
        this.value = Objects.requireNonNull(value);
        if (value instanceof NegatedValue) throw new UnsupportedOperationException();
    }

    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        EvaluationResult reValue = value.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder().compose(reValue);
        return builder.setValue(NegatedValue.negate(reValue.value)).build();
    }

    public static Value negate(@NotNull Value v) {
        Objects.requireNonNull(v);
        if (v instanceof BoolValue boolValue) {
            return boolValue.negate();
        }
        if (v instanceof NumericValue) {
            return ((NumericValue) v).negate();
        }
        if (v.isUnknown()) return v;

        if (v instanceof NegatedValue) return ((NegatedValue) v).value;
        if (v instanceof OrValue or) {
            Value[] negated = or.values.stream().map(NegatedValue::negate).toArray(Value[]::new);
            return new AndValue(v.getObjectFlow()).append(negated);
        }
        if (v instanceof AndValue and) {
            List<Value> negated = and.values.stream().map(NegatedValue::negate).collect(Collectors.toList());
            return new OrValue(v.getObjectFlow()).append(negated);
        }
        if (v instanceof EqualsValue equalsValue) {
            if (equalsValue.lhs instanceof NumericValue && equalsValue.rhs instanceof ConstrainedNumericValue) {
                Value improve = ((ConstrainedNumericValue) equalsValue.rhs).notEquals((NumericValue) equalsValue.lhs);
                if (improve != null) return improve;
            }
        }
        if (v instanceof SumValue) {
            return ((SumValue) v).negate();
        }
        if (v instanceof GreaterThanZeroValue) {
            return ((GreaterThanZeroValue) v).negate();
        }
        return new NegatedValue(v);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NegatedValue that = (NegatedValue) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        if (value.isNumeric()) {
            return "(-" + value + ")";
        }
        return "not (" + value + ")";
    }

    @Override
    public int order() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int internalCompareTo(Value v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int encodedSizeRestriction() {
        int sub = value.encodedSizeRestriction();
        if (sub == Level.SIZE_EMPTY) return Level.SIZE_NOT_EMPTY; // ==0 becomes >= 1
        if (sub == Level.SIZE_NOT_EMPTY) return Level.SIZE_EMPTY; // >=1 becomes == 0
        return 0; // not much we can do >=0 stays like that , ==5 cannot be replaced by sth else
    }

    // no need to implement any of the filter methods; they all do the same

    @Override
    public FilterResult filter(FilterMode filterMode, FilterMethod... filterMethods) {
        FilterResult filterResult = value.filter(filterMode, filterMethods);
        return new FilterResult(filterResult.accepted.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> NegatedValue.negate(e.getValue()), (v1, v2) -> v1)),
                NegatedValue.negate(filterResult.rest));
    }

    @Override
    public Set<Variable> variables() {
        return value.variables();
    }

    @Override
    public ParameterizedType type() {
        return value.type();
    }

    @Override
    public void visit(Consumer<Value> consumer) {
        value.visit(consumer);
        consumer.accept(this);
    }
}
