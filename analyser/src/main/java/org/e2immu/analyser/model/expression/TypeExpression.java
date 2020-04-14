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


import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Variable;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@E2Immutable
public class TypeExpression implements Expression {
    public final ParameterizedType parameterizedType;

    public TypeExpression(@NullNotAllowed ParameterizedType parameterizedType) {
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
    }

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        return parameterizedType.stream(); // TODO but there could be occasions where we need the FQN
    }

    @Override
    public int precedence() {
        return 17;
    }

    @Override
    @NotNull
    public Set<String> imports() {
        if (parameterizedType.typeInfo != null) return Set.of(parameterizedType.typeInfo.fullyQualifiedName);
        return Set.of();
    }
}
