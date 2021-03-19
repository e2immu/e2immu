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

package org.e2immu.analyser.visitor;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Message;

import java.util.Map;

public interface StatementAnalyserVisitor {

    record Data(StatementAnalyserResult result,
                int iteration,
                EvaluationContext evaluationContext,
                MethodInfo methodInfo, StatementAnalysis statementAnalysis,
                String statementId,
                Expression condition,
                Expression state,
                Expression absoluteState,
                ConditionManager conditionManagerForNextStatement,
                ConditionManager localConditionManager, // as at the start of the statement
                Map<String, AnalysisStatus> statusesAsMap) {

        // shortcut

        public String haveError(String message) {
            return statementAnalysis.messages.stream()
                    .filter(m -> m.message.contains(message))
                    .map(Message::toString)
                    .findFirst()
                    .orElse(null);
        }
        public int getProperty(Expression value, VariableProperty variableProperty) {
            return evaluationContext.getProperty(value, variableProperty, false);
        }

        @Override
        public String toString() {
            return "Data{" +
                    "iteration=" + iteration +
                    ", methodInfo=" + methodInfo +
                    ", statementAnalysis=" + statementAnalysis +
                    ", statementId='" + statementId + '\'' +
                    ", condition=" + condition +
                    ", state=" + state +
                    ", statusesAsMap=" + statusesAsMap +
                    ", evaluationContext=" + evaluationContext +
                    ", result=" + result +
                    '}';
        }

        public VariableInfo getFieldAsVariable(FieldInfo fieldInfo) {
            return statementAnalysis.getLatestVariableInfo(fieldInfo.fullyQualifiedName());
        }

        public VariableInfo getReturnAsVariable() {
            return statementAnalysis.getLatestVariableInfo(methodInfo.fullyQualifiedName());
        }

        public VariableInfo getThisAsVariable() {
            return statementAnalysis.getLatestVariableInfo(methodInfo.typeInfo.fullyQualifiedName() + ".this");
        }
    }

    void visit(Data data);
}
