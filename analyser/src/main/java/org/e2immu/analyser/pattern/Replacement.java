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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.annotation.Container;

import java.util.*;

/*
The system with local variables is as follows: we name all newly created local variables
lv$0, lv$1, etc. The actual replacement code will use the prefix that has been registered,
potentially expanded with some mechanism to make the variable unique in its context.
This local context is not known at the time of creating the replacement.

 */
public class Replacement {
    public static final Replacement NO_REPLACEMENT = new ReplacementBuilder("NO_REPLACEMENT", Pattern.NO_PATTERN ).build();

    public static final String LOCAL_VARIABLE_PREFIX = "lv$";

    public final List<Statement> statements;
    public final Pattern pattern;
    public final Map<String, LocalVariable> newLocalVariables;
    public final Set<String> namesCreatedInReplacement;
    public final String name;
    public final Map<Expression, TranslationMap> translationsOnExpressions;

    private Replacement(String name,
                        Pattern pattern,
                        List<Statement> statements,
                        Map<String, LocalVariable> newLocalVariables,
                        Set<String> namesCreatedInReplacement,
                        Map<Expression, TranslationMap> translationsOnExpressions) {
        this.name = name;
        this.statements = ImmutableList.copyOf(statements);
        this.pattern = pattern;
        this.newLocalVariables = ImmutableMap.copyOf(newLocalVariables);
        this.namesCreatedInReplacement = ImmutableSet.copyOf(namesCreatedInReplacement);
        this.translationsOnExpressions = ImmutableMap.copyOf(translationsOnExpressions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Replacement that = (Replacement) o;
        return pattern.equals(that.pattern) &&
                name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern, name);
    }

    @Container(builds = Replacement.class)
    public static class ReplacementBuilder {
        private final Pattern pattern;
        private final List<Statement> statements = new ArrayList<>();
        private int localVariableCounter;
        private final Map<String, LocalVariable> newLocalVariableNameToPrefix = new HashMap<>();
        private final String name;
        private final Set<String> namesCreatedInReplacement = new HashSet<>();
        private final Map<Expression, TranslationMap> translationsOnExpressions = new HashMap<>();

        public ReplacementBuilder(String name, Pattern pattern) {
            this.name = name;
            this.pattern = pattern;
        }

        public Replacement build() {
            return new Replacement(name, pattern, statements,
                    newLocalVariableNameToPrefix, namesCreatedInReplacement, translationsOnExpressions);
        }

        public void addStatement(Statement statement) {
            this.statements.add(statement);
            recursivelyAddNamesCreated(statement);
        }

        private void recursivelyAddNamesCreated(Statement statement) {
            Structure structure = statement.codeOrganization();
            recursivelyAddNamesCreated(structure);
            for (Structure subCo : structure.subStatements) {
                recursivelyAddNamesCreated(subCo);
            }
        }

        private void recursivelyAddNamesCreated(Structure structure) {
            for (Expression initialiser : structure.initialisers) {
                if (initialiser instanceof LocalVariableCreation) {
                    String name = ((LocalVariableCreation) initialiser).localVariable.name;
                    if (!name.startsWith(LOCAL_VARIABLE_PREFIX)) {
                        namesCreatedInReplacement.add(name);
                    }
                }
            }
            for (Statement sub : structure.getStatements()) {
                recursivelyAddNamesCreated(sub);
            }
        }

        public LocalVariable newLocalVariable(String prefix, ParameterizedType parameterizedType) {
            String key = LOCAL_VARIABLE_PREFIX + (localVariableCounter++);
            LocalVariable localVariableInMap = new LocalVariable(List.of(), prefix, parameterizedType, List.of());
            newLocalVariableNameToPrefix.put(key, localVariableInMap);
            return new LocalVariable(List.of(), key, parameterizedType, List.of());
        }

        public void applyTranslation(Expression expression, TranslationMap translationMap) {
            translationsOnExpressions.put(expression, translationMap);
        }
    }

}
