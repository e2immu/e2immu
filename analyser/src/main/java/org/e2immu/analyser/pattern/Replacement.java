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
import org.e2immu.analyser.model.CodeOrganization;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.annotation.Container;

import java.util.*;

/*
The system with local variables is as follows: we name all newly created local variables
lv$0, lv$1, etc. The actual replacement code will use the prefix that has been registered,
potentially expanded with some mechanism to make the variable unique in its context.
This local context is not known at the time of creating the replacement.

 */
public class Replacement {
    public static final String LOCAL_VARIABLE_PREFIX = "lv$";

    public final List<Statement> statements;
    public final Pattern pattern;
    public final Map<String, String> newLocalVariableNameToPrefix;
    public final Set<String> namesCreatedInReplacement;
    public final String name;

    private Replacement(String name,
                        Pattern pattern,
                        List<Statement> statements,
                        Map<String, String> newLocalVariableNameToPrefix,
                        Set<String> namesCreatedInReplacement) {
        this.name = name;
        this.statements = ImmutableList.copyOf(statements);
        this.pattern = pattern;
        this.newLocalVariableNameToPrefix = ImmutableMap.copyOf(newLocalVariableNameToPrefix);
        this.namesCreatedInReplacement = ImmutableSet.copyOf(namesCreatedInReplacement);
    }

    @Container(builds = Replacement.class)
    public static class ReplacementBuilder {
        private final Pattern pattern;
        private final List<Statement> statements = new ArrayList<>();
        private int localVariableCounter;
        private final Map<String, String> newLocalVariableNameToPrefix = new HashMap<>();
        private final String name;
        private final Set<String> namesCreatedInReplacement = new HashSet<>();

        public ReplacementBuilder(String name, Pattern pattern) {
            this.name = name;
            this.pattern = pattern;
        }

        public Replacement build() {
            return new Replacement(name, pattern, statements,
                    newLocalVariableNameToPrefix, namesCreatedInReplacement);
        }

        public void addStatement(Statement statement) {
            this.statements.add(statement);
            recursivelyAddNamesCreated(statement);
        }

        private void recursivelyAddNamesCreated(Statement statement) {
            CodeOrganization codeOrganization = statement.codeOrganization();
            recursivelyAddNamesCreated(codeOrganization);
            for (CodeOrganization subCo : codeOrganization.subStatements) {
                recursivelyAddNamesCreated(subCo);
            }
        }

        private void recursivelyAddNamesCreated(CodeOrganization codeOrganization) {
            for (Expression initialiser : codeOrganization.initialisers) {
                if (initialiser instanceof LocalVariableCreation) {
                    String name = ((LocalVariableCreation) initialiser).localVariable.name;
                    if (!name.startsWith(LOCAL_VARIABLE_PREFIX)) {
                        namesCreatedInReplacement.add(name);
                    }
                }
            }
            for (Statement sub : codeOrganization.statements.getStatements()) {
                recursivelyAddNamesCreated(sub);
            }
        }

        public String newLocalVariableName(String prefix) {
            String key = LOCAL_VARIABLE_PREFIX + (localVariableCounter++);
            newLocalVariableNameToPrefix.put(key, prefix);
            return key;
        }
    }

}
