/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.visitor;

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.MethodInfo;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface EvaluationResultVisitor {
    void visit(Data d);

    record Data(int iteration,
                MethodInfo methodInfo,
                String statementId,
                StatementAnalysis statementAnalysis,
                EvaluationResult evaluationResult) {

        public boolean haveSetProperty(String variableName, VariableProperty variableProperty) {
            return evaluationResult().getExpressionChangeStream().anyMatch(e -> e.getKey().fullyQualifiedName().equals(variableName)
                    && e.getValue().properties().containsKey(variableProperty));
        }

        public boolean haveLinkVariable(String fromName, Set<String> toNames) {
            return evaluationResult().getExpressionChangeStream()
                    .anyMatch(e -> fromName.equals(e.getKey().fullyQualifiedName()) &&
                            toNames.equals(e.getValue().linkedVariables().variables()
                                    .stream().map(v -> v.fullyQualifiedName()).collect(Collectors.toSet())));
        }

        public boolean haveMarkRead(String variableName) {
            return evaluationResult().getExpressionChangeStream().anyMatch(e -> e.getKey().fullyQualifiedName().equals(variableName) &&
                    !e.getValue().readAtStatementTime().isEmpty());
        }

        public boolean haveValueChange(String variableName) {
            return evaluationResult().getExpressionChangeStream().anyMatch(e -> e.getKey().fullyQualifiedName().equals(variableName));
        }

        public EvaluationResult.ChangeData findValueChange(String variableName) {
            return evaluationResult().getExpressionChangeStream().filter(e -> e.getKey().fullyQualifiedName().equals(variableName))
                    .map(Map.Entry::getValue).findFirst().orElseThrow();
        }

        public EvaluationResult.ChangeData findValueChangeByToString(String variableName) {
            return evaluationResult().getExpressionChangeStream().filter(e -> e.getKey().toString().equals(variableName))
                    .map(Map.Entry::getValue).findFirst().orElseThrow();
        }
    }
}
