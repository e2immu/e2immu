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


import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.Objects;

@E2Container
public class BooleanConstant extends BaseExpression implements ConstantExpression<Boolean>, Negatable {

    private final Primitives primitives;
    private final boolean constant;

    public BooleanConstant(Primitives primitives, boolean constant) {
        super(Identifier.constant(constant));
        this.primitives = primitives;
        this.constant = constant;
    }

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return primitives.booleanParameterizedType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BooleanConstant that = (BooleanConstant) o;
        return constant == that.constant;
    }

    public static EvaluationResult of(boolean b, EvaluationResult context) {
        Primitives primitives = context.getPrimitives();
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
        return builder.setExpression(new BooleanConstant(primitives, b)).build();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text(Boolean.toString(constant)));
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public int hashCode() {
        return Objects.hash(constant);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_CONSTANT_BOOLEAN;
    }

    @Override
    public int internalCompareTo(Expression v) {
        BooleanConstant bc = (BooleanConstant) v;
        if (constant == bc.constant) return 0;
        return constant ? -1 : 1;
    }

    public boolean constant() {
        return constant;
    }

    @Override
    public Boolean getValue() {
        return constant;
    }

    public Expression negate() {
        return new BooleanConstant(primitives, !constant);
    }
}
