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
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class ProductValue extends PrimitiveValue {
    public final Value lhs;
    public final Value rhs;
    private final Primitives primitives;

    public ProductValue(Primitives primitives, Value lhs, Value rhs, ObjectFlow objectFlow) {
        super(objectFlow);
        this.lhs = lhs;
        this.rhs = rhs;
        this.primitives = primitives;
    }

    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setValue(ProductValue.product(evaluationContext, reLhs.value, reRhs.value, getObjectFlow())).build();
    }

    // we try to maintain a sum of products
    public static Value product(EvaluationContext evaluationContext, Value l, Value r, ObjectFlow objectFlow) {
        Primitives primitives = evaluationContext.getAnalyserContext().getPrimitives();

        if (l instanceof NumericValue && l.toInt().value == 0 ||
                r instanceof NumericValue && r.toInt().value == 0) {
            return new IntValue(primitives, 0, ObjectFlow.NO_FLOW);
        }

        if (l instanceof NumericValue && l.toInt().value == 1) return r;
        if (r instanceof NumericValue && r.toInt().value == 1) return l;
        if (l instanceof NumericValue && r instanceof NumericValue)
            return new IntValue(primitives, l.toInt().value * r.toInt().value, ObjectFlow.NO_FLOW);

        // any unknown lingering
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;

        if (r instanceof SumValue sum) {
            return SumValue.sum(evaluationContext, product(evaluationContext, l, sum.lhs, objectFlow), product(evaluationContext, l, sum.rhs, objectFlow), objectFlow);
        }
        if (l instanceof SumValue sum) {
            return SumValue.sum(evaluationContext,
                    product(evaluationContext, sum.lhs, r, objectFlow),
                    product(evaluationContext, sum.rhs, r, objectFlow), objectFlow);
        }
        return l.compareTo(r) < 0 ? new ProductValue(primitives, l, r, objectFlow) : new ProductValue(primitives, r, l, objectFlow);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductValue orValue = (ProductValue) o;
        return lhs.equals(orValue.lhs) &&
                rhs.equals(orValue.rhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs);
    }

    @Override
    public String toString() {
        return lhs + " * " + rhs;
    }

    @Override
    public int order() {
        return ORDER_PRODUCT;
    }

    @Override
    public int internalCompareTo(Value v) {
        // comparing 2 or values...compare lhs
        int c = lhs.compareTo(((ProductValue) v).lhs);
        if (c == 0) {
            c = rhs.compareTo(((ProductValue) v).rhs);
        }
        return c;
    }

    @Override
    public Set<Variable> variables() {
        return SetUtil.immutableUnion(lhs.variables(), rhs.variables());
    }

    @Override
    public ParameterizedType type() {
        return primitives.widestType(lhs.type(), rhs.type());
    }

    @Override
    public void visit(Consumer<Value> consumer) {
        lhs.visit(consumer);
        rhs.visit(consumer);
        consumer.accept(this);
    }
}
