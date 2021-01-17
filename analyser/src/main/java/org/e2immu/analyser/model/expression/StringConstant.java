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

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.model.Diamond;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterizedType;
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
    public OutputBuilder output() {
        return new OutputBuilder().add(new Text(StringUtil.quote(constant)));
    }

    @Override
    public NewObject getInstance(EvaluationResult evaluationResult) {
        // TODO static flow
        // TODO apply code from method call to produce a decent state
        return NewObject.objectCreation(primitives, oneParameterConstructor(), primitives.stringParameterizedType, Diamond.NO,
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
