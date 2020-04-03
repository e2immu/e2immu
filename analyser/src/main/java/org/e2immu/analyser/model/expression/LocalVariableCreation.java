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
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.SideEffectContext;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@E2Immutable
public class LocalVariableCreation implements Expression {

    public final LocalVariable localVariable;
    public final Expression expression;

    public LocalVariableCreation(@NullNotAllowed LocalVariable localVariable,
                                 @NullNotAllowed Expression expression) {
        this.localVariable = Objects.requireNonNull(localVariable);
        this.expression = Objects.requireNonNull(expression);
    }

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return Primitives.PRIMITIVES.voidParameterizedType;
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(localVariable.annotations.stream().map(ann -> ann.stream() + " ").collect(Collectors.joining()))
                .append(localVariable.modifiers.stream().map(modifier -> modifier.toJava() + " ").collect(Collectors.joining()))
                .append(localVariable.parameterizedType.stream())
                .append(" ")
                .append(localVariable.name);
        if (expression != EmptyExpression.EMPTY_EXPRESSION) {
            sb.append(" = ").append(expression.expressionString(indent));
        }
        return sb.toString();
    }

    @Override
    public int precedence() {
        return 0;
    }

    @Override
    @NotNull
    @Independent
    public Set<String> imports() {
        Set<String> imports = new HashSet<>(expression.imports());
        ParameterizedType pt = localVariable.parameterizedType;
        if (pt.typeInfo != null) imports.add(pt.typeInfo.fullyQualifiedName);
        return ImmutableSet.copyOf(imports);
    }

    @Override
    @Independent
    @NotNull
    public List<Expression> subExpressions() {
        if (expression == EmptyExpression.EMPTY_EXPRESSION) return List.of();
        return List.of(expression);
    }

    @Override
    @Independent
    @NotNull
    public List<LocalVariableReference> newLocalVariables() {
        return List.of(new LocalVariableReference(localVariable, subExpressions()));
    }

    @Override
    public List<Variable> variablesUsed() {
        return expression.variablesUsed();
    }

    @Override
    @NotNull
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        // the creation itself is local; the assignment references in LocalVariableReference are what matters
        return SideEffect.LOCAL;
    }
}
