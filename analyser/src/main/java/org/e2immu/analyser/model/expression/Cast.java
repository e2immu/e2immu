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

import com.google.common.collect.ImmutableSet;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@E2Immutable
public class Cast implements Expression {
    public final Expression expression;
    public final ParameterizedType parameterizedType;

    public Cast(@NullNotAllowed Expression expression, @NullNotAllowed ParameterizedType parameterizedType) {
        this.expression = Objects.requireNonNull(expression);
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext) {
        // for now, casting will simply keep the value
        return expression.evaluate(evaluationContext);
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        return "(" + parameterizedType.stream() + ")" + bracketedExpressionString(indent, expression);
    }

    @Override
    public int precedence() {
        return 13;
    }

    @Override
    @NotNull
    public Set<String> imports() {
        Set<String> imports = new HashSet<>(expression.imports());
        if (parameterizedType.typeInfo != null) imports.add(parameterizedType.typeInfo.fullyQualifiedName);
        return ImmutableSet.copyOf(imports);
    }

    @Override
    @NotNull
    public List<Expression> subExpressions() {
        return List.of(expression);
    }


    @Override
    public List<InScopeSide> expressionsInScopeSide() {
        return List.of(new InScopeSide(expression, false));
    }
}
