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

import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.Objects;
import java.util.Set;

public class DivideValue extends PrimitiveValue {
    public final Value lhs;
    public final Value rhs;

    public DivideValue(Value lhs, Value rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public static Value divide(EvaluationContext evaluationContext, Value l, Value r) {
        if (l instanceof NumericValue && l.toInt().value == 0) return IntValue.ZERO_VALUE;
        if (r instanceof NumericValue && r.toInt().value == 0) {
            evaluationContext.raiseError(Message.DIVISION_BY_ZERO);
            return l;
        }
        if (r instanceof NumericValue && r.toInt().value == 1) return l;
        if (l instanceof IntValue && r instanceof IntValue)
            return new IntValue(l.toInt().value / r.toInt().value);

        // any unknown lingering
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;

        return new DivideValue(l, r);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DivideValue orValue = (DivideValue) o;
        return lhs.equals(orValue.lhs) &&
                rhs.equals(orValue.rhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs);
    }

    @Override
    public String toString() {
        return lhs + " / " + rhs;
    }

    @Override
    public int order() {
        return ORDER_DIVIDE;
    }

    @Override
    public int internalCompareTo(Value v) {
        // comparing 2 or values...compare lhs
        int c = lhs.compareTo(((DivideValue) v).lhs);
        if (c == 0) {
            c = rhs.compareTo(((DivideValue) v).rhs);
        }
        return c;
    }

    @Override
    public ParameterizedType type() {
        return Primitives.PRIMITIVES.widestType(lhs.type(), rhs.type());
    }

    @Override
    public Set<Variable> variables() {
        return SetUtil.immutableUnion(lhs.variables(), rhs.variables());
    }

    @Override
    public boolean isExpressionOfParameters() {
        return lhs.isExpressionOfParameters() && rhs.isExpressionOfParameters();
    }
}
