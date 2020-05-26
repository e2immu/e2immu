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

import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.Objects;
import java.util.Set;

public class ProductValue extends PrimitiveValue {
    public final Value lhs;
    public final Value rhs;

    public ProductValue(Value lhs, Value rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    // we try to maintain a sum of products
    public static Value product(Value l, Value r) {

        if (l instanceof NumericValue && l.toInt().value == 0) return IntValue.ZERO_VALUE;
        if (r instanceof NumericValue && r.toInt().value == 0) return IntValue.ZERO_VALUE;

        if (l instanceof NumericValue && l.toInt().value == 1) return r;
        if (r instanceof NumericValue && r.toInt().value == 1) return l;
        if (l instanceof NumericValue && r instanceof NumericValue)
            return new IntValue(l.toInt().value * r.toInt().value);
        if (l instanceof ConstrainedNumericValue && r instanceof NumericValue)
            return ((ConstrainedNumericValue) l).product(((NumericValue) r).getNumber());
        if (r instanceof ConstrainedNumericValue && l instanceof NumericValue)
            return ((ConstrainedNumericValue) r).product(((NumericValue) l).getNumber());
        if (r instanceof ConstrainedNumericValue && l instanceof ConstrainedNumericValue) {
            return ((ConstrainedNumericValue) l).product((ConstrainedNumericValue) r);
        }

        // any unknown lingering
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;

        if (r instanceof SumValue) {
            SumValue sum = (SumValue) r;
            return SumValue.sum(product(l, sum.lhs), product(l, sum.rhs));
        }
        if (l instanceof SumValue) {
            SumValue sum = (SumValue) l;
            return SumValue.sum(product(sum.lhs, r), product(sum.rhs, r));
        }
        return l.compareTo(r) < 0 ? new ProductValue(l, r) : new ProductValue(r, l);
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
        return Primitives.PRIMITIVES.widestType(lhs.type(), rhs.type());
    }
}
