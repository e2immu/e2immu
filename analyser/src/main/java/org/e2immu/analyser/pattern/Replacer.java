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

import org.e2immu.analyser.model.*;
import org.e2immu.annotation.UtilityClass;

import static org.e2immu.analyser.util.Logger.LogTarget.TRANSFORM;
import static org.e2immu.analyser.util.Logger.log;

import java.util.*;
import java.util.function.BiFunction;

@UtilityClass
public class Replacer {

    // TODO this class writes into statement analyser objects; should somehow be integrated with it.

    public static <T extends HasNavigationData<T>> void replace(EvaluationContext evaluationContext, MatchResult<T> matchResult, Replacement replacement) {
        if (!matchResult.pattern.equals(replacement.pattern)) throw new UnsupportedOperationException("Double check");

        log(TRANSFORM, "Start pasting replacement {} onto {} after matching pattern {}", replacement.name, matchResult.start.index(), replacement.pattern.name);
        if (replacement.statements.isEmpty()) return;
        TranslationMap update1 = applyTranslationsToPlaceholders(matchResult.translationMap, replacement.translationsOnExpressions);
        TranslationMap update2 = createNewLocalVariableNames(evaluationContext, update1,
                replacement.namesCreatedInReplacement, replacement.newLocalVariables);
        BiFunction<List<Statement>, String, T> generator = matchResult.start.generator(evaluationContext);
        List<T> replacementNsAtStartLevel = startReplacing(update2, generator, matchResult.start.index(), replacement.statements);

        matchResult.start.wireNext(replacementNsAtStartLevel.get(0));
        T lastReplacement = replacementNsAtStartLevel.get(replacementNsAtStartLevel.size() - 1);
        lastReplacement.wireNext(matchResult.next);
    }

    private static TranslationMap applyTranslationsToPlaceholders(TranslationMap translationMap,
                                                                  Map<Expression, TranslationMap> translationsOnExpressions) {
        Map<Expression, Expression> update = new HashMap<>();
        for (Map.Entry<? extends Expression, ? extends Expression> e : translationMap.expressions.entrySet()) {
            Expression template = e.getKey();
            Expression actual = e.getValue();
            TranslationMap inMap = translationsOnExpressions.get(template);
            if (inMap != null) {
                TranslationMap inMapUpdated = inMap.applyVariables(translationMap.variables);
                Expression translated = actual.translate(inMapUpdated);
                update.put(template, translated);
            }
        }
        return translationMap.overwriteExpressionMap(update);
    }

    private static TranslationMap createNewLocalVariableNames(EvaluationContext evaluationContext,
                                                              TranslationMap translationMap,
                                                              Set<String> namesCreatedInReplacement,
                                                              Map<String, LocalVariable> newLocalVariables) {
        Set<String> existing = new HashSet<>(evaluationContext.allUnqualifiedVariableNames());
        existing.addAll(namesCreatedInReplacement);
        Map<Variable, Variable> overwrite = new HashMap<>();
        for (Map.Entry<String, LocalVariable> e : newLocalVariables.entrySet()) {
            String dollarName = e.getKey();
            LocalVariable lv = e.getValue();
            String prefix = lv.name;
            String combination = prefix;
            if (existing.contains(combination)) {
                int counter = 0;
                while (existing.contains(combination = prefix + counter)) {
                    counter++;
                }
            }
            existing.add(combination);
            String newVariableName = combination;

            LocalVariableReference oldLvr = new LocalVariableReference(new LocalVariable(lv.modifiers,
                    dollarName, lv.parameterizedType, lv.annotations), List.of());
            LocalVariableReference newLvr = new LocalVariableReference(new LocalVariable(lv.modifiers,
                    newVariableName, lv.parameterizedType, lv.annotations), List.of());
            overwrite.put(oldLvr, newLvr);
        }
        return translationMap.overwriteVariableMap(overwrite);
    }

    private static <T extends HasNavigationData<T>> List<T> startReplacing(TranslationMap translationMap,
                                                                           BiFunction<List<Statement>, String, T> generator,
                                                                           String index,
                                                                           List<Statement> statements) {
        List<T> result = new LinkedList<>();
        String currentIndex = index;
        log(TRANSFORM, "Start replacing at {}", currentIndex);
        T previous = null;
        for (Statement statement : statements) {
            List<Statement> replacements = translationMap.translateStatement(statement);
            T newStatement = generator.apply(replacements, currentIndex);
            result.add(newStatement);
            T lastStatement = newStatement.lastStatement();
            currentIndex = incrementLastInIndices(lastStatement.index());
            if (previous != null) {
                previous.wireNext(newStatement);
            }
            previous = lastStatement;
        }
        return result;
    }

    private static String incrementLastInIndices(String index) {
        int pos = index.lastIndexOf(".");
        if (pos < 0) return Integer.toString(Integer.parseInt(index) + 1);
        return index.substring(0, pos + 1) + (Integer.parseInt(index.substring(pos + 1)) + 1);
    }
}
