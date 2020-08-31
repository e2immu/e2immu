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

        Map<String, String> newLocalVariableNames = createNewLocalVariableNames(evaluationContext,
                replacement.namesCreatedInReplacement,
                replacement.newLocalVariableNameToPrefix);
        log(TRANSFORM, "New local variable map: {}", newLocalVariableNames);

        List<NumberedStatement> replacementNsAtStartLevel = startReplacing(newLocalVariableNames,
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

    private static List<NumberedStatement> startReplacing(Map<String, String> newLocalVariableNames,
                                                          List<Integer> indices,
                                                          NumberedStatement parent,
                                                          List<Statement> statements) {
        List<NumberedStatement> result = new LinkedList<>();
        log(TRANSFORM, "Start replacing at {}", indices);
        int counter = indices.get(indices.size() - 1);
        for (Statement statement : statements) {
            List<Integer> newIndices = replaceLastInIndices(indices, counter);
            result.add(replace(newLocalVariableNames, statement, parent, newIndices));
            counter++;
        }
        return result;
    }

    private static List<Integer> replaceLastInIndices(List<Integer> indices, int counter) {
        ArrayList<Integer> res = new ArrayList<>(indices);
        res.set(res.get(indices.size() - 1), counter);
        return res;
    }

    private static NumberedStatement replace(Map<String, String> newLocalVariableNames,
                                             Statement statement,
                                             NumberedStatement parent,
                                             List<Integer> newIndices) {
        if (statement instanceof Pattern.PlaceHolderStatement)
            throw new UnsupportedOperationException("Should have been replaced");
        Statement newStatement = potentiallyReplaceLocalVariableCreation(statement, newLocalVariableNames);
        NumberedStatement numberedStatement = new NumberedStatement(newStatement, parent, newIndices);
        CodeOrganization codeOrganization = newStatement.codeOrganization();

        if (codeOrganization.expression instanceof Pattern.PlaceHolderExpression) {
            throw new UnsupportedOperationException("Should have been replaced");
        }
        if (codeOrganization.statements instanceof Block && codeOrganization.statements != Block.EMPTY_BLOCK) {
            // recurse into block(s)
            List<Integer> subIndices = ListUtil.immutableConcat(newIndices, List.of(0, 0));
            // TODO
        } else if (!codeOrganization.statements.getStatements().isEmpty()) {
            // TODO, inside switch block
        } else {
            numberedStatement.blocks.set(List.of());
        }

        int counter = 1;
        for (CodeOrganization subCo : codeOrganization.subStatements) {
            List<Integer> subIndices = ListUtil.immutableConcat(newIndices, List.of(counter, 0));
            // TODO
            ++counter;
        }

        return numberedStatement;
    }

    /*
    we cannot use the code organisation here (easily), since we need to produce a new statement
     */
    private static Statement potentiallyReplaceLocalVariableCreation(Statement statement, Map<String, String> newLocalVariableNames) {
        if (statement instanceof ExpressionAsStatement) {
            Expression expression = ((ExpressionAsStatement) statement).expression;
            if (expression instanceof LocalVariableCreation) {
                LocalVariableCreation localVariableCreation = (LocalVariableCreation) expression;
                String newName = newLocalVariableNames.get(localVariableCreation.localVariable.name);
                if (newName != null) {
                    LocalVariable lv = localVariableCreation.localVariable;
                    LocalVariable newLv = new LocalVariable(lv.modifiers, newName, lv.parameterizedType, lv.annotations);
                    log(TRANSFORM, "Replaced local variable creation {}", newName);
                    return new ExpressionAsStatement(new LocalVariableCreation(newLv, localVariableCreation.expression));
                }
            }
        }
        // TODO implement all other statements with initialisers (for, switch, try)
        return statement;
    }

}
