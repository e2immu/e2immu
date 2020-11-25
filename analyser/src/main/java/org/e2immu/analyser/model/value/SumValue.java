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
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class SumValue extends PrimitiveValue {
    public final Value lhs;
    public final Value rhs;
    private final Primitives primitives;

    public SumValue(Primitives primitives, Value lhs, Value rhs, ObjectFlow objectFlow) {
        super(objectFlow);
        this.lhs = lhs;
        this.rhs = rhs;
        this.primitives = primitives;
    }

    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setValue(SumValue.sum(evaluationContext, reLhs.value, reRhs.value, getObjectFlow())).build();
    }

    // we try to maintain a sum of products
    public static Value sum(EvaluationContext evaluationContext, Value l, Value r, ObjectFlow objectFlow) {
        Primitives primitives = evaluationContext.getPrimitives();

        if (l.equals(r)) return ProductValue.product(evaluationContext,
                new IntValue(primitives, 2, ObjectFlow.NO_FLOW), l, objectFlow);
        if (l instanceof IntValue && ((IntValue) l).value == 0) return r;
        if (r instanceof IntValue && ((IntValue) r).value == 0) return l;
        if (l instanceof NegatedValue && ((NegatedValue) l).value.equals(r) ||
                r instanceof NegatedValue && ((NegatedValue) r).value.equals(l)) {
            return new IntValue(primitives, 0, ObjectFlow.NO_FLOW);
        }
        if (l instanceof NumericValue && r instanceof NumericValue)
            return new IntValue(primitives, l.toInt().value + r.toInt().value, objectFlow);

        // any unknown lingering
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;

        // a + x*a
        if (l instanceof ProductValue && ((ProductValue) l).lhs instanceof NumericValue &&
                r.equals(((ProductValue) l).rhs))
            return ProductValue.product(evaluationContext,
                    new IntValue(primitives, 1 + ((ProductValue) l).lhs.toInt().value,
                            ((ProductValue) l).lhs.getObjectFlow()), r, objectFlow);
        if (r instanceof ProductValue && ((ProductValue) r).lhs instanceof NumericValue &&
                l.equals(((ProductValue) r).rhs))
            return ProductValue.product(evaluationContext, new IntValue(primitives, 1 + ((ProductValue) r).lhs.toInt().value,
                    ((ProductValue) r).lhs.getObjectFlow()), l, objectFlow);

        // n*a + m*a
        if (l instanceof ProductValue && r instanceof ProductValue &&
                ((ProductValue) l).lhs instanceof NumericValue && ((ProductValue) r).lhs instanceof NumericValue &&
                ((ProductValue) l).rhs.equals(((ProductValue) r).rhs)) {
            return ProductValue.product(evaluationContext,
                    new IntValue(primitives, ((ProductValue) l).lhs.toInt().value + ((ProductValue) r).lhs.toInt().value, objectFlow),
                    ((ProductValue) l).rhs, objectFlow);
        }
        return l.compareTo(r) < 0 ? new SumValue(primitives, l, r, objectFlow) : new SumValue(primitives, r, l, objectFlow);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SumValue orValue = (SumValue) o;
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
        return "(" + lhs.print(printMode) + " + " + rhs.print(printMode) + ")";
    }

    @Override
    public int order() {
        return ORDER_SUM;
    }

    @Override
    public int internalCompareTo(Value v) {
        // comparing 2 or values...compare lhs
        int c = lhs.compareTo(((SumValue) v).lhs);
        if (c == 0) {
            c = rhs.compareTo(((SumValue) v).rhs);
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

    // -(lhs + rhs) = -lhs + -rhs
    public Value negate(EvaluationContext evaluationContext) {
        return SumValue.sum(evaluationContext,
                NegatedValue.negate(evaluationContext, lhs),
                NegatedValue.negate(evaluationContext, rhs), getObjectFlow());
    }

    @Override
    public void visit(Predicate<Value> predicate) {
        if (predicate.test(this)) {
            lhs.visit(predicate);
            rhs.visit(predicate);
        }
    }

    @Override
    public boolean isNumeric() {
        return true;
    }
}
