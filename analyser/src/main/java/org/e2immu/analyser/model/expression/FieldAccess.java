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
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotNull;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@E2Immutable
public class FieldAccess implements Expression {
    public final Expression expression;
    public final Variable variable;

    public FieldAccess(Expression expression, Variable variable) {
        this.variable = Objects.requireNonNull(variable);
        this.expression = Objects.requireNonNull(expression);
    }

    @Override
    public ParameterizedType returnType() {
        return variable.parameterizedType();
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        return bracketedExpressionString(indent, expression) + "." + variable.name();
    }

    @Override
    public int precedence() {
        return 16;
    }

    @Override
    public Set<String> imports() {
        return expression.imports();
    }

    @Override
    @NotNull
    @Independent
    public List<Expression> subExpressions() {
        return List.of(expression);
    }

    @Override
    @NotNull
    @Independent
    public List<Variable> variablesUsed() {
        return ListUtil.immutableConcat(List.of(variable), expression.variablesUsed());
    }

    @Override
    @NotNull
    public Variable variableFromExpression() {
        return variable;
    }

    @Override
    @NotNull
    public Optional<Variable> assignmentTarget() {
        return Optional.of(variable);
    }

    @Override
    public List<InScopeSide> expressionsInScopeSide() {
        return List.of(new InScopeSide(expression, true));
    }
}
