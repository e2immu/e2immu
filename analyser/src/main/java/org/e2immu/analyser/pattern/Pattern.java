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

package org.e2immu.analyser.pattern;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.Container;

import java.util.*;
import java.util.stream.Collectors;

public class Pattern {
    public final static Pattern NO_PATTERN = new PatternBuilder("NO_PATTERN").build();

    public final String name;
    public final List<Statement> statements;
    public final List<ParameterizedType> types;
    public final List<Variable> variables;
    public final List<LocalVariable> localVariables;
    public final List<Expression> expressions;
    public final List<PlaceHolderStatement> placeHolderStatements;

    private Pattern(String name,
                    List<Statement> statements,
                    List<ParameterizedType> types,
                    List<LocalVariable> localVariables,
                    List<Variable> variables,
                    List<Expression> expressions,
                    List<PlaceHolderStatement> placeHolderStatements) {
        this.name = Objects.requireNonNull(name);
        this.statements = ImmutableList.copyOf(statements);
        this.types = ImmutableList.copyOf(types);
        this.variables = ImmutableList.copyOf(variables);
        this.expressions = ImmutableList.copyOf(expressions);
        this.localVariables = ImmutableList.copyOf(localVariables);
        this.placeHolderStatements = ImmutableList.copyOf(placeHolderStatements);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pattern pattern = (Pattern) o;
        return name.equals(pattern.name);
    }

    public int indexOfType(ParameterizedType type) {
        if (!type.isTypeParameter()) return -1;
        String typeParameterName = type.typeParameter.name;
        if (typeParameterName.startsWith(TYPE_PREFIX)) {
            try {
                return Integer.parseInt(typeParameterName.substring(TYPE_PREFIX.length()));
            } catch (NumberFormatException nfe) {
                // then not.
            }
        }
        return -1;
    }

    public int indexOfLocalVariable(String name) {
        if (name.startsWith(LOCAL_VAR_PREFIX)) {
            try {
                return Integer.parseInt(name.substring(LOCAL_VAR_PREFIX.length()));
            } catch (NumberFormatException nfe) {
                // then not.
            }
        }
        return -1;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Container(builds = Pattern.class)
    public static class PatternBuilder {
        private final String name;

        // by index
        private final List<ParameterizedType> types = new ArrayList<>();
        private final List<LocalVariable> localVariables = new ArrayList<>();
        private final List<Expression> expressions = new ArrayList<>();
        private final List<Variable> variables = new ArrayList<>();
        private final List<PlaceHolderStatement> placeHolderStatements = new ArrayList<>();
        private final List<Statement> statements = new ArrayList<>();

        public PatternBuilder(String name) {
            this.name = name;
        }

        public Pattern build() {
            return new Pattern(name, statements, types, localVariables, variables, expressions, placeHolderStatements);
        }

        public ParameterizedType matchType() {
            int index = types.size();
            TypeParameter typeParameter = new TypeParameter(Primitives.PRIMITIVES.objectTypeInfo, TYPE_PREFIX + index, index);
            typeParameter.typeParameterInspection.set(new TypeParameterInspection(List.of()));
            ParameterizedType type = new ParameterizedType(typeParameter, 0, ParameterizedType.WildCard.NONE);
            types.add(type);
            return type;
        }

        public Variable matchVariable(ParameterizedType type) {
            int index = variables.size();
            Variable v = new PlaceHolderVariable(index, type);
            variables.add(v);
            return v;
        }

        public LocalVariable matchLocalVariable(ParameterizedType type) {
            int index = localVariables.size();
            LocalVariable lv = new LocalVariable(List.of(), LOCAL_VAR_PREFIX + index, type, List.of());
            localVariables.add(lv);
            return lv;
        }

        public Expression matchSomeExpression(ParameterizedType returnType, Variable... variables) {
            int index = expressions.size();
            PlaceHolderExpression placeHolderExpression = new PlaceHolderExpression(index, returnType, Arrays.stream(variables).collect(Collectors.toSet()));
            expressions.add(placeHolderExpression);
            return placeHolderExpression;
        }

        public void addStatement(Statement statement) {
            this.statements.add(statement);
        }

        public void registerVariable(Variable v) {
            variables.add(v);
        }

        public Statement matchSomeStatements() {
            int index = placeHolderStatements.size();
            PlaceHolderStatement placeHolderStatement = new PlaceHolderStatement(index);
            placeHolderStatements.add(placeHolderStatement);
            return placeHolderStatement;
        }
    }

    public static class PlaceHolderVariable implements Variable {
        public final ParameterizedType parameterizedType;
        public final int index;

        public PlaceHolderVariable(int index, ParameterizedType parameterizedType) {
            this.index = index;
            this.parameterizedType = parameterizedType;
        }

        @Override
        public ParameterizedType concreteReturnType() {
            return parameterizedType;
        }

        @Override
        public ParameterizedType parameterizedType() {
            return parameterizedType;
        }

        @Override
        public String name() {
            return "v" + index;
        }

        @Override
        public String detailedString() {
            return "[var:" + name() + "]";
        }

        @Override
        public boolean isStatic() {
            return false;
        }

        @Override
        public SideEffect sideEffect(EvaluationContext evaluationContext) {
            return null;
        }

        @Override
        public int variableOrder() {
            return 0;
        }
    }

    public static class PlaceHolderExpression implements Expression {
        public final int index;
        public final Set<Variable> variablesToMatch;
        public final ParameterizedType returnType;

        // for testing
        public PlaceHolderExpression(int index) {
            this(index, ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR, Set.of());
        }

        public PlaceHolderExpression(int index, ParameterizedType returnType, Set<Variable> variablesToMatch) {
            this.index = index;
            this.variablesToMatch = ImmutableSet.copyOf(variablesToMatch);
            this.returnType = Objects.requireNonNull(returnType);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PlaceHolderExpression that = (PlaceHolderExpression) o;
            return index == that.index;
        }

        @Override
        public int hashCode() {
            return Objects.hash(index);
        }

        @Override
        public ParameterizedType returnType() {
            return returnType;
        }

        @Override
        public String expressionString(int indent) {
            return "expression(" + variablesToMatch.stream().map(Variable::name)
                    .collect(Collectors.joining(",")) + "):" + index;
        }

        @Override
        public int precedence() {
            return 0;
        }

        @Override
        public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return expressionString(0);
        }
    }

    public static class PlaceHolderStatement implements Statement {
        public final int index;

        public PlaceHolderStatement(int index) {
            this.index = index;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PlaceHolderStatement that = (PlaceHolderStatement) o;
            return index == that.index;
        }

        @Override
        public int hashCode() {
            return Objects.hash(index);
        }

        @Override
        public String statementString(int indent, NumberedStatement numberedStatement) {
            return null;
        }

        @Override
        public Set<String> imports() {
            return null;
        }

        @Override
        public SideEffect sideEffect(EvaluationContext evaluationContext) {
            return null;
        }

        @Override
        public Set<TypeInfo> typesReferenced() {
            return null;
        }

        @Override
        public Structure codeOrganization() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Statement translate(TranslationMap translationMap) {
            return null;
        }
    }

    public static final String LOCAL_VAR_PREFIX = "lv";

    public static final String TYPE_PREFIX = "T";
}
