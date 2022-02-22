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
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.EventuallyFinal;

import java.util.List;
import java.util.Objects;

@E2Container
public final class MemberValuePair extends BaseExpression implements Expression {

    public static final String VALUE = "value";
    private final String name;
    private final EventuallyFinal<Expression> value;

    public MemberValuePair(String name, EventuallyFinal<Expression> value) {
        super(Identifier.CONSTANT);
        this.name = name;
        this.value = value;
    }

    public MemberValuePair(@NotNull Expression value) {
        this(VALUE, value);
    }

    public MemberValuePair(@NotNull String name, @NotNull Expression value) {
        this(name, new EventuallyFinal<>());
        if (value instanceof UnevaluatedAnnotationParameterValue) {
            this.value.setVariable(value);
        } else {
            this.value.setFinal(value);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemberValuePair that = (MemberValuePair) o;
        return name.equals(that.name) &&
                value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(value.get());
    }

    @Override
    public ParameterizedType returnType() {
        return value.get().returnType();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        if (VALUE.equals(name)) return new OutputBuilder().add(value.get().output(qualification));
        return new OutputBuilder().add(new Text(name)).add(Symbol.assignment("=")).add(value.get().output(qualification));
    }

    @Override
    public Precedence precedence() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_MVP;
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException();
    }

    public String name() {
        return name;
    }

    public EventuallyFinal<Expression> value() {
        return value;
    }

    @Override
    public String toString() {
        return "MemberValuePair[" +
                "name=" + name + ", " +
                "value=" + value + ']';
    }

}
