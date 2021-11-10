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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
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

        public Message haveError(Message.Label message) {
            return statementAnalysis.localMessageStream()
                    .filter(m -> m.message() == message)
                    .findFirst()
                    .orElse(null);
        }

        public DV getProperty(Expression value, VariableProperty variableProperty) {
            return evaluationContext.getProperty(value, variableProperty, false, false);
        }

        public VariableInfo getFieldAsVariable(FieldInfo fieldInfo) {
            return statementAnalysis.getLatestVariableInfo(fieldInfo.fullyQualifiedName());
        }

        public VariableInfo getReturnAsVariable() {
            return statementAnalysis.getLatestVariableInfo(methodInfo.fullyQualifiedName());
        }
    }

    void visit(Data data);
}
