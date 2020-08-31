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
import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.model.LocalVariableReference;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.Variable;

import java.util.*;
import java.util.stream.Collectors;

public class MatchResult {
    public final NumberedStatement start;
    public final Pattern pattern;

    private MatchResult(Pattern pattern, NumberedStatement start) {
        this.start = start;
        this.pattern = pattern;
    }

    public String toString(int indent) {
        return "Match result for " + pattern.name + " starting at " + start.streamIndices();
    }

    public static class MatchResultBuilder {
        private final NumberedStatement start;
        private final Pattern pattern;
        private final Map<String, Variable> actualVariableNameToTemplateVariable = new HashMap<>();

        public MatchResultBuilder(Pattern pattern, NumberedStatement start) {
            this.pattern = pattern;
            this.start = start;
        }

        public MatchResult build() {
            return new MatchResult(pattern, start);
        }

        public void matchLocalVariable(LocalVariable templateVar, LocalVariable actualVar) {
            actualVariableNameToTemplateVariable.put(actualVar.name, new LocalVariableReference(templateVar, List.of()));
        }

        public void matchVariable(Variable varTemplate, Variable varActual) {
            Variable inMap = actualVariableNameToTemplateVariable.get(varActual.name());
            if (inMap == null) {
                actualVariableNameToTemplateVariable.put(varActual.name(), varTemplate);
            } else if (!inMap.equals(varTemplate)) {
                throw new UnsupportedOperationException();
            }
        }

        public boolean containsAllVariables(Set<Variable> templateVar, List<Variable> actualVariables) {
            Set<Variable> translatedActual = actualVariables.stream()
                    .map(v -> actualVariableNameToTemplateVariable.getOrDefault(v.name(), v))
                    .collect(Collectors.toSet());
            return translatedActual.containsAll(templateVar);
        }
    }
}
