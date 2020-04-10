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

import com.google.common.collect.Sets;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Variable;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@E2Immutable
public class ArrayAccess implements Expression {

    public final Expression expression;
    public final Expression index;

    public ArrayAccess(@NullNotAllowed Expression expression, @NullNotAllowed Expression index) {
        this.expression = Objects.requireNonNull(expression);
        this.index = Objects.requireNonNull(index);
    }

    @Override
    public ParameterizedType returnType() {
        return expression.returnType().copyWithOneFewerArrays();
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        return bracketedExpressionString(indent, expression) + "[" + index.expressionString(indent) + "]";
    }

    @Override
    public int precedence() {
        return 16;
    }

    @Override
    @NotNull
    public Set<String> imports() {
        return Sets.union(expression.imports(), index.imports());
    }

    @Override
    @NotNull
    public List<Expression> subExpressions() {
        return List.of(expression, index);
    }

    @Override
    public Optional<Variable> assignmentTarget() {
        return expression.assignmentTarget();
    }

    @Override
    public Variable variableFromExpression() {
        return expression.variableFromExpression();
    }
}
