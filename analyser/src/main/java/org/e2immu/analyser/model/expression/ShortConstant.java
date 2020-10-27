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


import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.ShortValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.Objects;

@E2Container
public class ShortConstant implements ConstantExpression<Short> {
    private final Primitives primitives;

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return primitives.shortParameterizedType;
    }

    @NotNull
    public final short constant;

    public ShortConstant(Primitives primitives, short constant) {
        this.primitives = primitives;
        this.constant = constant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShortConstant that = (ShortConstant) o;
        return constant == that.constant;
    }

    @Override
    public int hashCode() {
        return Objects.hash(constant);
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        return Short.toString(constant);
    }

    @Override
    public int precedence() {
        return 17; // highest
    }

    @Override
    public Value newValue() {
        return new ShortValue(primitives, constant, ObjectFlow.NO_FLOW);
    }

    @Override
    public Short getValue() {
        return constant;
    }
}
