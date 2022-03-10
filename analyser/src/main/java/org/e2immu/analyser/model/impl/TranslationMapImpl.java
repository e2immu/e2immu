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

package org.e2immu.analyser.model.impl;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.TranslationCollectors;
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
public class TranslationMapImpl implements TranslationMap {

    public final Map<? extends Variable, ? extends Variable> variables;
    public final Map<MethodInfo, MethodInfo> methods;
    public final Map<? extends Expression, ? extends Expression> expressions;
    public final Map<? extends Statement, List<Statement>> statements;
    public final Map<ParameterizedType, ParameterizedType> types;
    public final Map<LocalVariable, LocalVariable> localVariables;
    public final Map<? extends Variable, ? extends Expression> variableExpressions;
    public final boolean expandDelayedWrappedExpressions;

    public TranslationMapImpl(Map<? extends Statement, List<Statement>> statements,
                              Map<? extends Expression, ? extends Expression> expressions,
                              Map<? extends Variable, ? extends Expression> variableExpressions,
                              Map<? extends Variable, ? extends Variable> variables,
                              Map<MethodInfo, MethodInfo> methods,
                              Map<ParameterizedType, ParameterizedType> types,
                              boolean expandDelayedWrappedExpressions) {
        this.variables = variables;
        this.expressions = expressions;
        this.variableExpressions = variableExpressions;
        this.statements = statements;
        this.methods = methods;
        this.types = types;
        localVariables = variables.entrySet().stream()
                .filter(e -> e.getKey() instanceof LocalVariableReference && e.getValue() instanceof LocalVariableReference)
                .collect(Collectors.toMap(e -> ((LocalVariableReference) e.getKey()).variable,
                        e -> ((LocalVariableReference) e.getValue()).variable));
        this.expandDelayedWrappedExpressions = expandDelayedWrappedExpressions;
    }

    @Override
    public boolean expandDelayedWrappedExpressions() {
        return expandDelayedWrappedExpressions;
    }

    @Override
    public String toString() {
        return "TM{" + variables.size() + "," + methods.size() + "," + expressions.size() + "," + statements.size()
                + "," + types.size() + "," + localVariables.size() + "," + variableExpressions.size() +
                (expandDelayedWrappedExpressions ? ",expand" : "") + "}";
    }

    @Override
    public boolean hasVariableTranslations() {
        return !variables.isEmpty();
    }

    @Override
    public Expression translateExpression(Expression expression) {
        return Objects.requireNonNullElse(expressions.get(expression), expression);
    }

    @Override
    public MethodInfo translateMethod(MethodInfo methodInfo) {
        return methods.getOrDefault(methodInfo, methodInfo);
    }

    @Override
    public Variable translateVariable(Variable variable) {
        return Objects.requireNonNullElse(variables.get(variable), variable);
    }

    @Override
    public Expression translateVariableExpressionNullIfNotTranslated(Variable variable) {
        return variableExpressions.get(variable);
    }

    @Override
    public List<Statement> translateStatement(Statement statement) {
        List<Statement> list = statements.get(statement);
        if (list == null) {
            return List.of(statement.translate(this));
        }
        return list.stream().map(st -> st.translate(this)).collect(Collectors.toList());
    }

    @Override
    public Block translateBlock(Block block) {
        List<Statement> list = translateStatement(block);
        if (list.size() != 1) throw new UnsupportedOperationException();
        return (Block) list.get(0);
    }

    @Override
    public ParameterizedType translateType(ParameterizedType parameterizedType) {
        ParameterizedType inMap = types.get(parameterizedType);
        if (inMap != null) return inMap;
        List<ParameterizedType> params = parameterizedType.parameters;
        List<ParameterizedType> translatedTypes = params.isEmpty() ? params :
                params.stream().map(this::translateType).collect(TranslationCollectors.toList(params));
        if (params == translatedTypes) return parameterizedType;
        return new ParameterizedType(parameterizedType.typeInfo, parameterizedType.arrays,
                parameterizedType.wildCard, translatedTypes);
    }

    @Override
    public TypeInfo translateTypeWithBody(TypeInfo typeInfo) {
        return typeInfo; // TODO
    }

    @Override
    public LocalVariable translateLocalVariable(LocalVariable localVariable) {
        return localVariables.getOrDefault(localVariable, localVariable).translate(this);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Expression> T ensureExpressionType(Expression expression, Class<T> clazz) {
        if (clazz.isAssignableFrom(expression.getClass())) return (T) expression;
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return statements.isEmpty() && expressions.isEmpty() && methods.isEmpty() &&
                types.isEmpty() && variables.isEmpty() && localVariables.isEmpty() && variableExpressions.isEmpty();
    }

    @Override
    public TranslationMap update(Map<Variable, Expression> variableExpressionMap) {
        TranslationMapImpl.Builder builder = new TranslationMapImpl.Builder();
        builder.variables.putAll(variables);
        builder.expressions.putAll(expressions);
        builder.methods.putAll(methods);
        builder.statements.putAll(statements);
        builder.types.putAll(types);
        builder.variableExpressions.putAll(variableExpressions);
        builder.variableExpressions.putAll(variableExpressionMap);
        return builder.build();
    }

    @Container(builds = TranslationMapImpl.class)
    public static class Builder {
        private final Map<Variable, Variable> variables = new HashMap<>();
        private final Map<Expression, Expression> expressions = new HashMap<>();
        private final Map<Variable, Expression> variableExpressions = new HashMap<>();
        private final Map<MethodInfo, MethodInfo> methods = new HashMap<>();
        private final Map<Statement, List<Statement>> statements = new HashMap<>();
        private final Map<ParameterizedType, ParameterizedType> types = new HashMap<>();
        private boolean expandDelayedWrappedExpressions;

        public TranslationMapImpl build() {
            return new TranslationMapImpl(statements, expressions, variableExpressions, variables, methods, types,
                    expandDelayedWrappedExpressions);
        }

        public Builder put(Statement template, Statement actual) {
            statements.put(template, List.of(actual));
            return this;
        }

        public Builder put(MethodInfo template, MethodInfo actual) {
            methods.put(template, actual);
            return this;
        }

        public Builder put(Statement template, List<Statement> statements) {
            this.statements.put(template, statements);
            return this;
        }

        public Builder put(Expression template, Expression actual) {
            expressions.put(template, actual);
            return this;
        }

        public Builder addVariableExpression(Variable variable, Expression actual) {
            variableExpressions.put(variable, actual);
            assert actual.isDelayed() || !actual.variables(true).contains(variable)
                    : "No self-references allowed! Variable: " + variable;
            return this;
        }

        public Builder put(ParameterizedType template, ParameterizedType actual) {
            types.put(template, actual);
            return this;
        }

        public Builder put(Variable template, Variable actual) {
            variables.put(template, actual);
            return this;
        }

        public boolean translateMethod(MethodInfo methodInfo) {
            return methods.containsKey(methodInfo);
        }

        public boolean isEmpty() {
            return statements.isEmpty() && expressions.isEmpty() && variables.isEmpty() && methods.isEmpty() && types.isEmpty();
        }

        public Builder setExpandDelayedWrapperExpressions(boolean expandDelayedWrappedExpressions) {
            this.expandDelayedWrappedExpressions = expandDelayedWrappedExpressions;
            return this;
        }
    }
}
