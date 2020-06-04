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
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class EqualsValue extends PrimitiveValue {
    public final Value lhs;
    public final Value rhs;

    public EqualsValue(Value lhs, Value rhs) {
        boolean swap = lhs.compareTo(rhs) > 0;
        this.lhs = swap ? rhs : lhs;
        this.rhs = swap ? lhs : rhs;
    }

    public static Value equals(Value l, Value r) {
        if (l.equals(r)) return BoolValue.TRUE;
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
        if (l instanceof ConstrainedNumericValue && ((ConstrainedNumericValue) l).rejects(r)) return BoolValue.FALSE;
        if (r instanceof ConstrainedNumericValue && ((ConstrainedNumericValue) r).rejects(l)) return BoolValue.FALSE;
        return new EqualsValue(l, r);
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
    public Map<Variable, Boolean> individualNullClauses() {
        return lhs instanceof NullValue && rhs instanceof ValueWithVariable ? Map.of(((ValueWithVariable) rhs).variable, true) :
                Map.of();
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

    @Override
    public Map<Variable, Value> individualSizeRestrictions() {
        // constants always left, methods always right
        if (lhs instanceof NumericValue && rhs instanceof MethodValue) {
            MethodValue methodValue = (MethodValue) rhs;
            if (methodValue.methodInfo.typeInfo.hasSize()) {
                int sizeOnMethod = methodValue.methodInfo.methodAnalysis.get().getProperty(VariableProperty.SIZE);
                if (sizeOnMethod >= Level.TRUE && methodValue.object instanceof VariableValue) {
                    VariableValue variableValue = (VariableValue) methodValue.object;
                    return Map.of(variableValue.variable, this);
                }
            }
        }
        return Map.of();
    }

    @Override
    public int encodedSizeRestriction() {
        if (lhs instanceof NumericValue && lhs.isDiscreteType()) {
            int size = ((NumericValue) lhs).getNumber().intValue();
            return Analysis.encodeSizeEquals(size);
        }
        return 0;
    }

    @Override
    public boolean isExpressionOfParameters() {
        return lhs.isExpressionOfParameters() && rhs.isExpressionOfParameters();
    }
}
