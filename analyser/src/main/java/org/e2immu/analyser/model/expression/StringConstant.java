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


import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.StringValue;
import org.e2immu.analyser.parser.Primitives;

import java.util.Objects;

@E2Immutable
public class StringConstant implements Expression, Constant<String> {
    @Override
    @NotNull
    public ParameterizedType returnType() {
        return Primitives.PRIMITIVES.stringParameterizedType;
    }

    public final StringValue constant;

    @Override
    public Value evaluate(EvaluationContext evaluationContext) {
        return constant;
    }

    public StringConstant(@NullNotAllowed String constant) {
        this.constant = new StringValue(Objects.requireNonNull(constant));
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        return "\"" + constant.value.replace("\"", "\\\"") + "\"";
    }

    @Override
    public int precedence() {
        return 17;
    }

    @Override
    public String getValue() {
        return constant.value;
    }
}
