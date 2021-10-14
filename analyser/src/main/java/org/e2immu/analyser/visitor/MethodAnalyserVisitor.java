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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Message;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface MethodAnalyserVisitor {
    void visit(Data data);

    record Data(int iteration,
                EvaluationContext evaluationContext,
                MethodInfo methodInfo,
                MethodAnalysis methodAnalysis,
                List<ParameterAnalysis> parameterAnalyses,
                Map<String, AnalysisStatus> statuses,
                Supplier<Stream<Message>> messageStream) {

        public int getProperty(Expression value, VariableProperty variableProperty) {
            return evaluationContext.getProperty(value, variableProperty, false, false);
        }

        public VariableInfo getFieldAsVariable(FieldInfo fieldInfo) {
            StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
            return lastStatement == null ? null : lastStatement.getLatestVariableInfo(fieldInfo.fullyQualifiedName());
        }

        public VariableInfo getReturnAsVariable() {
            return methodAnalysis.getLastStatement().getLatestVariableInfo(methodInfo.fullyQualifiedName());
        }

        public VariableInfo getThisAsVariable() {
            return methodAnalysis.getLastStatement().getLatestVariableInfo(methodInfo.typeInfo.fullyQualifiedName + ".this");
        }

        public String haveError(Message.Label message) {
            return messageStream.get()
                    .filter(m -> m.message() == message)
                    .map(Message::toString)
                    .findFirst()
                    .orElse(null);
        }
        public int falseFrom1() {
            return iteration == 0 ? Level.DELAY: Level.FALSE;
        }
        public int falseFrom2() {
            return iteration <= 1 ? Level.DELAY: Level.FALSE;
        }
    }

}
