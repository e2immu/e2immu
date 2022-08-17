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
import org.e2immu.annotation.NotNull;

import java.util.Objects;

public final class DoubleConstant extends BaseExpression implements ConstantExpression<Double>, Numeric {
    private final Primitives primitives;
    private final double constant;

    public DoubleConstant(Primitives primitives, double constant) {
        super(Identifier.constant(constant));
        this.primitives = primitives;
        this.constant = constant;
    }

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return primitives.doubleParameterizedType();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_CONSTANT_DOUBLE;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return (int) Math.signum(constant - ((DoubleConstant) v).constant);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DoubleConstant that = (DoubleConstant) o;
        return Double.compare(that.constant, constant) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(constant);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text(Double.toString(constant)));
    }

    @Override
    public Double getValue() {
        return constant;
    }

    @Override
    public Expression negate() {
        return new DoubleConstant(primitives, -constant);
    }

    @Override
    public Number getNumber() {
        return constant;
    }

    @Override
    public boolean isNumeric() {
        return true;
    }

    public Primitives primitives() {
        return primitives;
    }

    public double constant() {
        return constant;
    }

}
