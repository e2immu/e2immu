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

package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.EvaluationResult;
import org.e2immu.analyser.model.MethodInfo;

import java.util.Set;
import java.util.stream.Collectors;

public interface EvaluationResultVisitor {
    void visit(Data d);

    record Data(int iteration, String step, MethodInfo methodInfo, String statementId,
                EvaluationResult evaluationResult) {

        public boolean haveSetProperty(String variableName, VariableProperty variableProperty, int value) {
            return evaluationResult().getModificationStream().filter(sam -> sam instanceof StatementAnalyser.SetProperty)
                    .map(sam -> (StatementAnalyser.SetProperty) sam)
                    .anyMatch(sp -> variableName.equals(sp.variable.fullyQualifiedName()) && variableProperty == sp.property && value == sp.value);
        }

        public boolean haveLinkVariable(String fromName, Set<String> toNames) {
            return evaluationResult().getModificationStream().filter(sam -> sam instanceof StatementAnalyser.LinkVariable)
                    .map(sam -> (StatementAnalyser.LinkVariable) sam)
                    .anyMatch(lv -> fromName.equals(lv.variable.fullyQualifiedName()) &&
                            toNames.equals(lv.to.stream().map(v -> v.fullyQualifiedName()).collect(Collectors.toSet())));
        }

        public boolean haveMarkRead(String variableName) {
            return evaluationResult().getModificationStream().filter(sam -> sam instanceof StatementAnalyser.MarkRead)
                    .map(sam -> (StatementAnalyser.MarkRead) sam)
                    .anyMatch(mr -> variableName.equals(mr.variable.fullyQualifiedName()));
        }

        public boolean haveMarkAssigned(String variableName) {
            return evaluationResult().getModificationStream().filter(sam -> sam instanceof StatementAnalyser.MarkAssigned)
                    .map(sam -> (StatementAnalyser.MarkAssigned) sam)
                    .anyMatch(ma -> variableName.equals(ma.variable.fullyQualifiedName()));
        }
    }
}
