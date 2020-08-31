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
import org.e2immu.analyser.analyser.methodanalysercomponent.CreateNumberedStatements;
import org.e2immu.analyser.model.*;
import org.e2immu.annotation.UtilityClass;

import static org.e2immu.analyser.util.Logger.LogTarget.TRANSFORM;
import static org.e2immu.analyser.util.Logger.log;

import java.util.*;

@UtilityClass
public class Replacer {

    public static void replace(EvaluationContext evaluationContext, MatchResult matchResult, Replacement replacement) {
        if (!matchResult.pattern.equals(replacement.pattern)) throw new UnsupportedOperationException("Double check");

        log(TRANSFORM, "Start pasting replacement {} onto {} after matching pattern {}", replacement.name,
                matchResult.start.index, replacement.pattern.name);
        if (replacement.statements.isEmpty()) return;
        TranslationMap update1 = applyTranslationsToPlaceholders(matchResult.translationMap, replacement.translationsOnExpressions);
        TranslationMap update2 = createNewLocalVariableNames(evaluationContext, update1,
                replacement.namesCreatedInReplacement, replacement.newLocalVariables);

        List<NumberedStatement> replacementNsAtStartLevel = startReplacing(update2,
                matchResult.start.indices, matchResult.start.parent, replacement.statements);
        wireStart(replacementNsAtStartLevel.get(0), matchResult.start);
        if (matchResult.start.next.get().isPresent()) {
            wireEnd(replacementNsAtStartLevel.get(replacementNsAtStartLevel.size() - 1), matchResult.start.next.get().get());
        }
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

    private static void wireEnd(NumberedStatement lastReplacement, NumberedStatement nextOriginal) {
        lastReplacement.next.set(Optional.of(nextOriginal));
    }

    private static void wireStart(NumberedStatement firstReplacement, NumberedStatement start) {
        start.replacement.set(firstReplacement);
    }

    private static List<NumberedStatement> startReplacing(TranslationMap translationMap,
                                                          List<Integer> indices,
                                                          NumberedStatement parent,
                                                          List<Statement> statements) {
        List<NumberedStatement> result = new LinkedList<>();
        List<Integer> currentIndices = indices;
        log(TRANSFORM, "Start replacing at {}", currentIndices);
        NumberedStatement previous = null;
        for (Statement statement : statements) {
            List<NumberedStatement> resultingNumberedStatements = replace(translationMap, statement, parent, currentIndices);
            result.addAll(resultingNumberedStatements);
            NumberedStatement lastOne = result.get(result.size() - 1);
            currentIndices = incrementLastInIndices(lastOne.indices);
            if (previous != null) {
                previous.next.set(Optional.of(resultingNumberedStatements.get(0)));
            }
            previous = lastOne;
        }
        return result;
    }

    private static List<Integer> incrementLastInIndices(List<Integer> indices) {
        ArrayList<Integer> res = new ArrayList<>(indices);
        res.set(indices.size() - 1, res.get(indices.size() - 1) + 1);
        return res;
    }

    private static List<NumberedStatement> replace(TranslationMap translationMap,
                                                   Statement statement,
                                                   NumberedStatement parent,
                                                   List<Integer> newIndices) {
        List<Statement> replacements = translationMap.translateStatement(statement);
        List<NumberedStatement> result = new LinkedList<>();
        Stack<Integer> indicesInStack = new Stack<>();
        indicesInStack.addAll(newIndices);
        CreateNumberedStatements.recursivelyCreateNumberedStatements(parent, replacements, indicesInStack, result, false);
        return result;
    }

}
