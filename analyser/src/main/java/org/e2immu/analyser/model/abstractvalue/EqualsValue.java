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
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class EqualsValue extends PrimitiveValue {
    public final Value lhs;
    public final Value rhs;
    private final Primitives primitives;

    // public for testing
    public EqualsValue(Primitives primitives, Value lhs, Value rhs, ObjectFlow objectFlow) {
        super(objectFlow);
        boolean swap = lhs.compareTo(rhs) > 0;
        this.lhs = swap ? rhs : lhs;
        this.rhs = swap ? lhs : rhs;
        this.primitives = primitives;
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setValue(EqualsValue.equals(evaluationContext, reLhs.value, reRhs.value, objectFlow)).build();
    }

    public static Value equals(EvaluationContext evaluationContext, Value l, Value r, ObjectFlow objectFlow) {
        Primitives primitives = evaluationContext.getPrimitives();
        if (l.equals(r)) return new BoolValue(primitives, true, objectFlow);
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;

        if (l instanceof NullValue && evaluationContext.isNotNull0(r) ||
                r instanceof NullValue && evaluationContext.isNotNull0(l))
            return new BoolValue(primitives, false, objectFlow);

        return new EqualsValue(primitives, l, r, objectFlow);
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return lhs.print(printMode) + " == " + rhs.print(printMode);
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
        return primitives.booleanParameterizedType;
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
