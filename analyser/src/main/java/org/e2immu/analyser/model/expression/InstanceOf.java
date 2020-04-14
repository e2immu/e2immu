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
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.InstanceOfValue;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InstanceOf implements Expression {
    public final ParameterizedType parameterizedType;
    public final Expression expression;

    public InstanceOf(Expression expression, ParameterizedType parameterizedType) {
        this.parameterizedType = parameterizedType;
        this.expression = expression;
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext) {
        if (expression instanceof VariableExpression || expression instanceof FieldAccess)
            return new InstanceOfValue(expression.variableFromExpression(), parameterizedType);
        if (expression instanceof ClassExpression) {
            return BoolValue.of(parameterizedType.isAssignableFrom(((ClassExpression) expression).parameterizedType));
        }
        // this error occurs with a TypeExpression, probably due to our code giving priority to types rather than
        // variable names, when you use a type name as a variable name, which is perfectly allowed in Java but is
        // horrible practice. We leave the bug for now.
        throw new UnsupportedOperationException("? have expression of " + expression.getClass());
    }

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return Primitives.PRIMITIVES.booleanParameterizedType;
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        return bracketedExpressionString(indent, expression) + " instanceof " + parameterizedType.stream();
    }

    @Override
    public int precedence() {
        return 9;
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
