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
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;

public class MemberValuePair implements Expression {

    public final String name;
    public final Expression value;

    public MemberValuePair(@NotNull String name, @NotNull Expression value) {
        this.value = Objects.requireNonNull(value);
        this.name = Objects.requireNonNull(name);
    }

    @Override
    public List<Expression> subExpressions() {
        return List.of(value);
    }

    @Override
    public ParameterizedType returnType() {
        return value.returnType();
    }

    @Override
    public String expressionString(int indent) {
        return name + " = " + value.expressionString(indent);
    }

    @Override
    public int precedence() {
        return 1;
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor) {
        throw new UnsupportedOperationException();
    }
}
