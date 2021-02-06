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

package org.e2immu.analyser.model.expression;


import com.google.common.math.DoubleMath;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.Objects;

@E2Container
public record IntConstant(Primitives primitives,
                          int constant,
                          ObjectFlow objectFlow) implements ConstantExpression<Integer>, Negatable, Numeric {

    public IntConstant(Primitives primitives, int constant) {
        this(primitives, constant, ObjectFlow.NO_FLOW);
    }

    public static Expression intOrDouble(Primitives primitives, double b, ObjectFlow objectFlow) {
        if (DoubleMath.isMathematicalInteger(b)) {
            long l = Math.round(b);
            if (l > Integer.MAX_VALUE || l < Integer.MIN_VALUE) {
                return new LongConstant(primitives, l, objectFlow);
            }
            return new IntConstant(primitives, (int) l, objectFlow);
        }
        return new DoubleConstant(primitives, b, objectFlow);
    }

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return primitives.intParameterizedType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntConstant that = (IntConstant) o;
        return constant == that.constant;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return constant - ((IntConstant) v).constant;
    }

    @Override
    public int hashCode() {
        return Objects.hash(constant);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_CONSTANT_INT;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public Integer getValue() {
        return constant;
    }

    @Override
    public Number getNumber() {
        return constant;
    }

    @Override
    public OutputBuilder output() {
        return new OutputBuilder().add(new Text(Integer.toString(constant)));
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public Expression negate() {
        return new IntConstant(primitives, -constant, objectFlow);
    }

    @Override
    public boolean isNumeric() {
        return true;
    }
}
