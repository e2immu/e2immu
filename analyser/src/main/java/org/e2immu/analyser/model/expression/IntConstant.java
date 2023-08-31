/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model.expression;


import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.IntUtil;
import org.e2immu.annotation.NotNull;

import java.util.Objects;

public final class IntConstant extends BaseExpression implements ConstantExpression<Integer>, Negatable, Numeric {
    private final Primitives primitives;
    private final int constant;

    public IntConstant(Primitives primitives, Identifier identifier, int constant) {
        super(identifier, constant > 1 || constant < -1 ? 2 : 1);
        this.constant = constant;
        this.primitives = primitives;
    }

    public IntConstant(Primitives primitives, int constant) {
        this(primitives, Identifier.constant(constant), constant);
    }

    public static Expression intOrDouble(Primitives primitives, Identifier identifier, double b) {
        if (IntUtil.isMathematicalInteger(b)) {
            long l = Math.round(b);
            if (l > Integer.MAX_VALUE || l < Integer.MIN_VALUE) {
                return new LongConstant(primitives, identifier, l);
            }
            return new IntConstant(primitives, identifier, (int) l);
        }
        return new DoubleConstant(primitives, identifier, b);
    }

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return primitives.intParameterizedType();
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
    public Integer getValue() {
        return constant;
    }

    @Override
    public Number getNumber() {
        return constant;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text(Integer.toString(constant)));
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public Expression negate() {
        return new IntConstant(primitives, identifier, -constant);
    }

    @Override
    public boolean isNumeric() {
        return true;
    }

    public Primitives primitives() {
        return primitives;
    }

    public int constant() {
        return constant;
    }

}
