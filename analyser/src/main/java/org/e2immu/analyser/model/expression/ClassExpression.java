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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.objectflow.ObjectFlow;
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
                              ParameterizedType parameterizedClassType, // Class<String>
                              ObjectFlow objectFlow) implements ConstantExpression<ParameterizedType> {

    public ClassExpression(Primitives primitives, ParameterizedType parameterizedType) {
        this(primitives, parameterizedType,
                new ParameterizedType(primitives.classTypeInfo, List.of(parameterizedType)),
                ObjectFlow.NO_FLOW);
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
    public ObjectFlow getObjectFlow() {
        return objectFlow;
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
}
