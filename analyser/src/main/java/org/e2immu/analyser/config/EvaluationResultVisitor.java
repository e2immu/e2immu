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

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.MethodInfo;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface EvaluationResultVisitor {
    void visit(Data d);

    record Data(int iteration, MethodInfo methodInfo, String statementId, EvaluationResult evaluationResult) {

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

        public EvaluationResult.ExpressionChangeData findValueChange(String variableName) {
            return evaluationResult().getExpressionChangeStream().filter(e -> e.getKey().fullyQualifiedName().equals(variableName))
                    .map(Map.Entry::getValue).findFirst().orElseThrow();
        }
    }
}
