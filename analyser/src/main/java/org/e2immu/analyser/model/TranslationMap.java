/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.model;

import org.e2immu.analyser.model.expression.ArrayInitializer;
import org.e2immu.analyser.model.statement.Block;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class TranslationMap {

    public static final TranslationMap EMPTY_MAP = new TranslationMap(Map.of(), Map.of(), Map.of(), Map.of());

    public final Map<? extends Variable, ? extends Variable> variables;
    public final Map<? extends Expression, ? extends Expression> expressions;
    public final Map<? extends Statement, ? extends Statement> statements;
    public final Map<ParameterizedType, ParameterizedType> types;
    public final Map<LocalVariable, LocalVariable> localVariables;

    public TranslationMap(Map<? extends Statement, ? extends Statement> statements,
                          Map<? extends Expression, ? extends Expression> expressions,
                          Map<? extends Variable, ? extends Variable> variables,
                          Map<ParameterizedType, ParameterizedType> types) {
        this.variables = variables;
        this.expressions = expressions;
        this.statements = statements;
        this.types = types;
        localVariables = variables.entrySet().stream()
                .filter(e -> e.getKey() instanceof LocalVariableReference && e.getValue() instanceof LocalVariableReference)
                .collect(Collectors.toMap(e -> ((LocalVariableReference) e.getKey()).variable,
                        e -> ((LocalVariableReference) e.getValue()).variable));
    }

    public static TranslationMap fromVariableMap(Map<? extends Variable, ? extends Variable> map) {
        return new TranslationMap(Map.of(), Map.of(), map, Map.of());
    }

    public Expression translateExpression(Expression expression) {
        return Objects.requireNonNullElse(expressions.get(expression), expression).translate(this);
    }

    public Variable translateVariable(Variable variable) {
        return Objects.requireNonNullElse(variables.get(variable), variable);
    }

    public Statement translateStatement(Statement statement) {
        return Objects.requireNonNullElse(statements.get(statement), statement).translate(this);
    }

    public Block translateBlock(Block block) {
        return ensureStatementType(translateStatement(block), Block.class);
    }

    public ParameterizedType translateType(ParameterizedType parameterizedType) {
        ParameterizedType inMap = types.get(parameterizedType);
        if (inMap != null) return inMap;
        if (parameterizedType.parameters.isEmpty()) return parameterizedType;
        return new ParameterizedType(parameterizedType.typeInfo, parameterizedType.arrays,
                parameterizedType.wildCard,
                parameterizedType.parameters.stream().map(this::translateType).collect(Collectors.toList()));
    }

    public LocalVariable translateLocalVariable(LocalVariable localVariable) {
        return localVariables.getOrDefault(localVariable, localVariable);
    }

    public static <T extends Expression> T ensureExpressionType(Expression expression, Class<T> clazz) {
        if (clazz.isAssignableFrom(expression.getClass())) return (T) expression;
        throw new UnsupportedOperationException();
    }

    public static <T extends Statement> T ensureStatementType(Statement statement, Class<T> clazz) {
        if (clazz.isAssignableFrom(statement.getClass())) return (T) statement;
        throw new UnsupportedOperationException();
    }
}
