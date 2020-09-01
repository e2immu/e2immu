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

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class MatchResult {
    public final NumberedStatement start;
    public final Pattern pattern;
    public final TranslationMap translationMap;
    public final NumberedStatement next;

    private MatchResult(Pattern pattern, NumberedStatement start, NumberedStatement next, TranslationMap translationMap) {
        this.start = start;
        this.pattern = pattern;
        this.translationMap = translationMap;
        this.next = next;
    }

    public static class MatchResultBuilder {
        private final NumberedStatement start;
        private final Pattern pattern;
        // template variable matching is one-on-one, so we keep maps in both directions
        private final Map<String, Variable> actualVariableNameToTemplateVariable = new HashMap<>();
        private final Map<String, Variable> templateVariableNameToActualVariable = new HashMap<>();
        private final TranslationMap.TranslationMapBuilder translationMapBuilder = new TranslationMap.TranslationMapBuilder();
        private NumberedStatement next;

        public MatchResultBuilder(Pattern pattern, NumberedStatement start) {
            this.pattern = pattern;
            this.start = start;
        }

        public MatchResult build() {
            return new MatchResult(pattern, start, next, translationMapBuilder.build());
        }

        public void matchLocalVariable(LocalVariable templateVar, LocalVariable actualVar) {
            actualVariableNameToTemplateVariable.put(actualVar.name, new LocalVariableReference(templateVar, List.of()));
            templateVariableNameToActualVariable.put(templateVar.name, new LocalVariableReference(actualVar, List.of()));

            if (pattern.indexOfType(templateVar.parameterizedType) >= 0) {
                translationMapBuilder.put(templateVar.parameterizedType, actualVar.parameterizedType);
            }
        }

        public PatternMatcher.SimpleMatchResult matchVariable(Variable varTemplate, Variable varActual) {
            Variable actualInMap = templateVariableNameToActualVariable.get(varTemplate.name());
            if (actualInMap != null && !varActual.equals(actualInMap)) return PatternMatcher.SimpleMatchResult.NO;
            actualVariableNameToTemplateVariable.put(varActual.name(), varTemplate);
            templateVariableNameToActualVariable.put(varTemplate.name(), varActual);
            translationMapBuilder.put(varTemplate, varActual);
            return PatternMatcher.SimpleMatchResult.YES;
        }

        public boolean containsAllVariables(Set<Variable> templateVar, List<Variable> actualVariables) {
            Set<Variable> translatedActual = actualVariables.stream()
                    .map(v -> actualVariableNameToTemplateVariable.getOrDefault(v.name(), v))
                    .collect(Collectors.toSet());
            return translatedActual.containsAll(templateVar);
        }

        public void registerPlaceholderExpression(Pattern.PlaceHolderExpression template, Expression actual) {
            translationMapBuilder.put(template, actual);
        }

        public void setNext(NumberedStatement next) {
            this.next = next;
        }
    }
}
