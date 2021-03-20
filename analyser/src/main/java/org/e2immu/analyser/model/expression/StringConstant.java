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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;

@E2Container
public record StringConstant(Primitives primitives,
                             String constant,
                             ObjectFlow objectFlow) implements ConstantExpression<String> {

    public StringConstant(Primitives primitives, String constant) {
        this(primitives, constant, ObjectFlow.NO_FLOW);
    }

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return primitives.stringParameterizedType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StringConstant that = (StringConstant) o;
        return constant.equals(that.constant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(constant);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_CONSTANT_STRING;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return constant.compareTo(((StringConstant) v).constant);
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public String getValue() {
        return constant;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text(StringUtil.quote(constant)));
    }

    @Override
    public NewObject getInstance(EvaluationResult evaluationResult) {
        // TODO static flow
        // TODO apply code from method call to produce a decent state
        return NewObject.objectCreation("StringConstant-" + constant,
                primitives, oneParameterConstructor(), primitives.stringParameterizedType, Diamond.NO,
                List.of(this), ObjectFlow.NO_FLOW);
    }

    private MethodInfo oneParameterConstructor() {
        return primitives.stringTypeInfo.typeInspection.get().methods().stream()
                .filter(mi -> mi.isConstructor && mi.methodInspection.get().getParameters().size() == 1 &&
                        mi.methodInspection.get().getParameters().get(0).parameterizedType().typeInfo ==
                                primitives.stringTypeInfo)
                .findFirst().orElse(null);
    }
}
