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
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class DivideValue extends PrimitiveValue {
    public final Value lhs;
    public final Value rhs;
    private final Primitives primitives;

    public DivideValue(Primitives primitives, Value lhs, Value rhs, ObjectFlow objectFlow) {
        super(objectFlow);
        this.lhs = lhs;
        this.rhs = rhs;
        this.primitives = primitives;
    }

    public static EvaluationResult divide(EvaluationContext evaluationContext, Value l, Value r, ObjectFlow objectFlow) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

        if (l instanceof NumericValue && l.toInt().value == 0) return builder.setValue(l).build();
        if (r instanceof NumericValue && r.toInt().value == 0) {
            builder.raiseError(Message.DIVISION_BY_ZERO);
            return builder.setValue(l).build();
        }
        if (r instanceof NumericValue && r.toInt().value == 1) return builder.setValue(l).build();
        Primitives primitives = evaluationContext.getPrimitives();

        if (l instanceof IntValue && r instanceof IntValue)
            return builder.setValue(new IntValue(primitives, l.toInt().value / r.toInt().value, objectFlow)).build();

        // any unknown lingering
        if (l.isUnknown() || r.isUnknown()) {
            return builder.setValue(UnknownPrimitiveValue.UNKNOWN_PRIMITIVE).build();
        }

        return builder.setValue(new DivideValue(primitives, l, r, objectFlow)).build();
    }

    @Override
    public boolean isNumeric() {
        return true;
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
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return lhs.print(printMode) + " / " + rhs.print(printMode);
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
        return primitives.widestType(lhs.type(), rhs.type());
    }

    @Override
    public Set<Variable> variables() {
        return SetUtil.immutableUnion(lhs.variables(), rhs.variables());
    }

    @Override
    public void visit(Predicate<Value> predicate) {
        if (predicate.test(this)) {
            lhs.visit(predicate);
            rhs.visit(predicate);
        }
    }
}
