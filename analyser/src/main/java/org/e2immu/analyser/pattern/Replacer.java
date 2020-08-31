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
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.util.ListUtil;
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


        List<NumberedStatement> replacementNsAtStartLevel = startReplacing(matchResult.translationMap,
                matchResult.start.indices, matchResult.start.parent, replacement.statements);
        wireStart(replacementNsAtStartLevel.get(0), matchResult.start);
        if (matchResult.start.next.get().isPresent()) {
            wireEnd(replacementNsAtStartLevel.get(replacementNsAtStartLevel.size() - 1), matchResult.start.next.get().get());
        }
    }

    private static Map<String, String> createNewLocalVariableNames(EvaluationContext evaluationContext,
                                                                   Set<String> namesCreatedInReplacement,
                                                                   Map<String, String> newLocalVariableNameToPrefix) {
        Set<String> existing = new HashSet<>(evaluationContext.allUnqualifiedVariableNames());
        existing.addAll(namesCreatedInReplacement);
        Map<String, String> newNames = new HashMap<>();
        for (Map.Entry<String, String> e : newLocalVariableNameToPrefix.entrySet()) {
            String prefix = e.getValue();
            int counter = 0;
            String combination;
            while (existing.contains(combination = prefix + counter)) {
                counter++;
            }
            existing.add(combination);
            newNames.put(e.getKey(), combination);
        }
        return newNames;
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
        for (Statement statement : statements) {
            List<NumberedStatement> resultingNumberedStatements = replace(translationMap, statement, parent, currentIndices);
            result.addAll(resultingNumberedStatements);
            currentIndices = incrementLastInIndices(result.get(result.size() - 1).indices);
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
