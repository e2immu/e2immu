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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.E2Container;

import java.util.List;
import java.util.Objects;

/**
 * Represents an expression like String.class
 */
@E2Container
public record ClassExpression(Primitives primitives,
                              ParameterizedType parameterizedType, // String
                              ParameterizedType parameterizedClassType // Class<String>
) implements ConstantExpression<ParameterizedType> {

    public ClassExpression(Primitives primitives, ParameterizedType parameterizedType) {
        this(primitives, parameterizedType, new ParameterizedType(primitives.classTypeInfo, List.of(parameterizedType)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassExpression that = (ClassExpression) o;
        return parameterizedType.equals(that.parameterizedType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new ClassExpression(primitives, translationMap.translateType(parameterizedType));
    }

    @Override
    public int internalCompareTo(Expression v) {
        return parameterizedType.detailedString().compareTo(((ClassExpression) v).parameterizedType.detailedString());
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_CONSTANT_CLASS;
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedClassType;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(parameterizedType.output(qualification)).add(Symbol.DOT).add(new Text("class"));
    }

    @Override
    public Precedence precedence() {
        return Precedence.UNARY;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return parameterizedType.typesReferenced(true);
    }

    @Override
    public ParameterizedType getValue() {
        return parameterizedType;
    }

    @Override
    public Identifier getIdentifier() {
        return Identifier.CONSTANT;
    }
}
