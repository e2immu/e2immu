/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model;

import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Container;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Translation takes place from statement, over expression, down to variable and type.
 * <p>
 * Blocks can only translate into blocks;
 * statements can translate into lists of statements.
 */
@E2Container
public class TranslationMap {

    public static final TranslationMap EMPTY_MAP = new TranslationMap(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    public final Map<? extends Variable, ? extends Variable> variables;
    public final Map<MethodInfo, MethodInfo> methods;
    public final Map<? extends Expression, ? extends Expression> expressions;
    public final Map<? extends Statement, List<Statement>> statements;
    public final Map<ParameterizedType, ParameterizedType> types;
    public final Map<LocalVariable, LocalVariable> localVariables;

    public TranslationMap(Map<? extends Statement, List<Statement>> statements,
                          Map<? extends Expression, ? extends Expression> expressions,
                          Map<? extends Variable, ? extends Variable> variables,
                          Map<MethodInfo, MethodInfo> methods,
                          Map<ParameterizedType, ParameterizedType> types) {
        this.variables = variables;
        this.expressions = expressions;
        this.statements = statements;
        this.methods = methods;
        this.types = types;
        localVariables = variables.entrySet().stream()
                .filter(e -> e.getKey() instanceof LocalVariableReference && e.getValue() instanceof LocalVariableReference)
                .collect(Collectors.toMap(e -> ((LocalVariableReference) e.getKey()).variable,
                        e -> ((LocalVariableReference) e.getValue()).variable));
    }

    @Override
    public String toString() {
        return "TM{" +
                List.of(variables.isEmpty() ? "" : variables.toString(),
                        types.isEmpty() ? "" : types.toString())
                        .stream().filter(s -> !s.isEmpty()).collect(Collectors.joining("")) +
                '}';
    }

    public static TranslationMap fromVariableMap(Map<? extends Variable, ? extends Variable> map) {
        return new TranslationMap(Map.of(), Map.of(), map, Map.of(), Map.of());
    }

    public TranslationMap overwriteVariableMap(Map<? extends Variable, ? extends Variable> map) {
        Map<Variable, Variable> overwrittenMap = new HashMap<>(variables);
        overwrittenMap.putAll(map);
        return new TranslationMap(statements, expressions, overwrittenMap, methods, types);
    }

    public TranslationMap overwriteExpressionMap(Map<Expression, Expression> update) {
        Map<Expression, Expression> overwrittenExpressionMap = new HashMap<>(expressions);
        overwrittenExpressionMap.putAll(update);
        return new TranslationMap(statements, overwrittenExpressionMap, variables, methods, types);
    }

    public Expression translateExpression(Expression expression) {
        return Objects.requireNonNullElse(expressions.get(expression), expression).translate(this);
    }

    public Expression directExpression(Expression expression) {
        return expressions.get(expression);
    }

    public MethodInfo translateMethod(MethodInfo methodInfo) {
        return methods.getOrDefault(methodInfo, methodInfo);
    }

    public Variable translateVariable(Variable variable) {
        return Objects.requireNonNullElse(variables.get(variable), variable);
    }

    public List<Statement> translateStatement(Statement statement) {
        List<Statement> list = statements.get(statement);
        if (list == null) {
            return List.of(statement.translate(this));
        }
        return list.stream().map(st -> st.translate(this)).collect(Collectors.toList());
    }

    public Block translateBlock(Block block) {
        List<Statement> list = translateStatement(block);
        if (list.size() != 1) throw new UnsupportedOperationException();
        return (Block) list.get(0);
    }

    public ParameterizedType translateType(ParameterizedType parameterizedType) {
        ParameterizedType inMap = types.get(parameterizedType);
        if (inMap != null) return inMap;
        if (parameterizedType.parameters.isEmpty()) return parameterizedType;
        return new ParameterizedType(parameterizedType.typeInfo, parameterizedType.arrays,
                parameterizedType.wildCard,
                parameterizedType.parameters.stream().map(this::translateType).collect(Collectors.toList()));
    }

    public TypeInfo translateTypeWithBody(TypeInfo typeInfo) {
        return typeInfo; // TODO
    }

    public LocalVariable translateLocalVariable(LocalVariable localVariable) {
        return localVariables.getOrDefault(localVariable, localVariable).translate(this);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Expression> T ensureExpressionType(Expression expression, Class<T> clazz) {
        if (clazz.isAssignableFrom(expression.getClass())) return (T) expression;
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Statement> T ensureStatementType(List<Statement> statements, Class<T> clazz) {
        if (statements.size() != 1) throw new UnsupportedOperationException();
        Statement statement = statements.get(0);
        if (clazz.isAssignableFrom(statement.getClass())) return (T) statement;
        throw new UnsupportedOperationException();
    }

    public TranslationMap applyVariables(Map<? extends Variable, ? extends Variable> variables) {
        Map<? extends Variable, ? extends Variable> updatedVariables =
                this.variables.entrySet().stream().collect(Collectors.toMap(e -> {
                    Variable inMap = variables.get(e.getKey());
                    return inMap == null ? e.getKey() : inMap;
                }, Map.Entry::getValue));
        return new TranslationMap(statements, expressions, updatedVariables, methods, types);
    }

    public boolean isEmpty() {
        return statements.isEmpty() && expressions.isEmpty() && methods.isEmpty() &&
                types.isEmpty() && variables.isEmpty() && localVariables.isEmpty();
    }

    @Container(builds = TranslationMap.class)
    public static class TranslationMapBuilder {
        private final Map<Variable, Variable> variables = new HashMap<>();
        private final Map<Expression, Expression> expressions = new HashMap<>();
        private final Map<MethodInfo, MethodInfo> methods = new HashMap<>();
        private final Map<Statement, List<Statement>> statements = new HashMap<>();
        private final Map<ParameterizedType, ParameterizedType> types = new HashMap<>();

        public TranslationMap build() {
            return new TranslationMap(statements, expressions, variables, methods, types);
        }

        public TranslationMapBuilder put(Statement template, Statement actual) {
            statements.put(template, List.of(actual));
            return this;
        }

        public TranslationMapBuilder put(MethodInfo template, MethodInfo actual) {
            methods.put(template, actual);
            return this;
        }

        public TranslationMapBuilder put(Statement template, List<Statement> statements) {
            this.statements.put(template, statements);
            return this;
        }

        public TranslationMapBuilder put(Expression template, Expression actual) {
            expressions.put(template, actual);
            return this;
        }

        public TranslationMapBuilder put(ParameterizedType template, ParameterizedType actual) {
            types.put(template, actual);
            return this;
        }

        public TranslationMapBuilder put(Variable template, Variable actual) {
            variables.put(template, actual);
            return this;
        }

        public boolean translateMethod(MethodInfo methodInfo) {
            return methods.containsKey(methodInfo);
        }
    }
}
