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
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.Objects;
import java.util.Set;

public class RemainderValue extends PrimitiveValue {
    public final Value lhs;
    public final Value rhs;

    public RemainderValue(Value lhs, Value rhs, ObjectFlow objectFlow) {
        super(objectFlow);
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public static Value remainder(EvaluationContext evaluationContext, Value l, Value r, ObjectFlow objectFlow) {
        if (l instanceof NumericValue && l.toInt().value == 0) return l;
        if (r instanceof NumericValue && r.toInt().value == 0) {
            evaluationContext.raiseError(Message.DIVISION_BY_ZERO);
            return l;
        }
        if (r instanceof NumericValue && r.toInt().value == 1) return l;
        if (l instanceof IntValue && r instanceof IntValue)
            return new IntValue(l.toInt().value % r.toInt().value, objectFlow);

        // any unknown lingering
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;

        return new RemainderValue(l, r, objectFlow);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemainderValue orValue = (RemainderValue) o;
        return lhs.equals(orValue.lhs) &&
                rhs.equals(orValue.rhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs);
    }

    @Override
    public String toString() {
        return lhs + " % " + rhs;
    }

    @Override
    public int order() {
        return ORDER_REMAINDER;
    }

    @Override
    public int internalCompareTo(Value v) {
        // comparing 2 or values...compare lhs
        int c = lhs.compareTo(((RemainderValue) v).lhs);
        if (c == 0) {
            c = rhs.compareTo(((RemainderValue) v).rhs);
        }
        return c;
    }

    @Override
    public Set<Variable> variables() {
        return SetUtil.immutableUnion(lhs.variables(), rhs.variables());
    }

    @Override
    public ParameterizedType type() {
        return Primitives.PRIMITIVES.widestType(lhs.type(), rhs.type());
    }

    @Override
    public boolean isExpressionOfParameters() {
        return lhs.isExpressionOfParameters() && rhs.isExpressionOfParameters();
    }
}
