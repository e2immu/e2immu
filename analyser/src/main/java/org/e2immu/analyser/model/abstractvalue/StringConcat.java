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
import org.e2immu.analyser.model.value.ConstantValue;
import org.e2immu.analyser.model.value.StringValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class StringConcat implements Value {
    public final Value lhs;
    public final Value rhs;
    public final ObjectFlow objectFlow;
    private final ParameterizedType stringParameterizedType;

    private StringConcat(Value lhs, Value rhs, ParameterizedType stringParameterizedType, ObjectFlow objectFlow) {
        this.lhs = lhs;
        this.rhs = rhs;
        this.objectFlow = objectFlow;
        this.stringParameterizedType = stringParameterizedType;
    }

    public static Value stringConcat(EvaluationContext evaluationContext, Value l, Value r, ObjectFlow objectFlow) {
        StringValue lsv = l.asInstanceOf(StringValue.class);
        StringValue rsv = r.asInstanceOf(StringValue.class);
        Primitives primitives = evaluationContext.getPrimitives();

        if (lsv != null && rsv != null) {
            return lsv.value.isEmpty() ? r : rsv.value.isEmpty() ? l : new StringValue(primitives, lsv.value + rsv.value, objectFlow);
        }
        ConstantValue rcv = r.asInstanceOf(ConstantValue.class);
        if (lsv != null && rcv != null) {
            return new StringValue(primitives, lsv.value + rcv.toString(), objectFlow);
        }
        ConstantValue lcv = l.asInstanceOf(ConstantValue.class);
        if (rsv != null && lcv != null) {
            return new StringValue(primitives, lcv.toString() + rsv.value, objectFlow);
        }
        // any unknown lingering
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;

        return new StringConcat(l, r, primitives.stringParameterizedType, objectFlow);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return switch (variableProperty) {
            case CONTAINER -> Level.TRUE;
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            case NOT_NULL -> MultiLevel.EFFECTIVELY_NOT_NULL;
            default -> Level.FALSE;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StringConcat orValue = (StringConcat) o;
        return lhs.equals(orValue.lhs) &&
                rhs.equals(orValue.rhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs);
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return lhs.print(printMode) + " + " + rhs.print(printMode);
    }

    @Override
    public int order() {
        return ORDER_SUM;
    }

    @Override
    public int internalCompareTo(Value v) {
        // comparing 2 or values...compare lhs
        int c = lhs.compareTo(((StringConcat) v).lhs);
        if (c == 0) {
            c = rhs.compareTo(((StringConcat) v).rhs);
        }
        return c;
    }

    @Override
    public ParameterizedType type() {
        return stringParameterizedType;
    }

    @Override
    public Set<Variable> variables() {
        return SetUtil.immutableUnion(lhs.variables(), rhs.variables());
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public void visit(Predicate<Value> predicate) {
        if (predicate.test(this)) {
            lhs.visit(predicate);
            rhs.visit(predicate);
        }
    }

    @Override
    public Instance getInstance(EvaluationContext evaluationContext) {
        return new Instance(type(), getObjectFlow(), UnknownValue.EMPTY);
    }
}
