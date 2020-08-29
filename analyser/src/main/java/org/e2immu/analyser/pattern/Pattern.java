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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.Container;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Pattern {

    public final List<Statement> statements;
    public final List<ParameterizedType> types = new ArrayList<>();
    public final List<Variable> variables = new ArrayList<>();
    public final List<Expression> expressions = new ArrayList<>();

    private Pattern(List<Statement> statements) {
        this.statements = ImmutableList.copyOf(statements);
    }

    @Container(builds = Pattern.class)
    public static class PatternBuilder {

        // by index
        private final List<ParameterizedType> types = new ArrayList<>();
        private final List<LocalVariable> localVariables = new ArrayList<>();
        private final List<Expression> expressions = new ArrayList<>();

        private final List<Statement> statements = new ArrayList<>();

        public Pattern build() {
            return new Pattern(statements);
        }

        public ParameterizedType matchType() {
            int index = types.size();
            TypeParameter typeParameter = new TypeParameter(Primitives.PRIMITIVES.objectTypeInfo, TYPE_PREFIX + index, index);
            ParameterizedType type = new ParameterizedType(typeParameter, 0, ParameterizedType.WildCard.NONE);
            types.add(type);
            return type;
        }

        public LocalVariable matchLocalVariable(ParameterizedType type) {
            int index = localVariables.size();
            LocalVariable lv = new LocalVariable(List.of(), LOCAL_VAR_PREFIX + index, type, List.of());
            localVariables.add(lv);
            return lv;
        }

        public Expression matchSomeExpression(Variable... variables) {
            int index = expressions.size();
            PlaceHolder placeHolder = new PlaceHolder(index, Arrays.stream(variables).collect(Collectors.toSet()));
            expressions.add(placeHolder);
            return placeHolder;
        }

        public void addStatement(Statement statement) {
            this.statements.add(statement);
        }
    }

    public static class PlaceHolder implements Expression {
        public final int index;
        public final Set<Variable> variablesToMatch;

        public PlaceHolder(int index, Set<Variable> variablesToMatch) {
            this.index = index;
            this.variablesToMatch = ImmutableSet.copyOf(variablesToMatch);
        }

        @Override
        public ParameterizedType returnType() {
            return null;
        }

        @Override
        public String expressionString(int indent) {
            return "[expression(" + variablesToMatch.stream().map(Variable::name)
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
    }

    public static final String LOCAL_VAR_PREFIX = "lv";

    public static final String TYPE_PREFIX = "T";
}
