package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.StatementAnalysis;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.parser.Message;

import java.util.Map;

public interface StatementAnalyserVisitor {

    record Data(AnalysisStatus analysisStatus, int iteration,
                    EvaluationContext evaluationContext,
                    MethodInfo methodInfo, StatementAnalysis statementAnalysis,
                    String statementId, Value condition, Value state, Map<String, AnalysisStatus> statusesAsMap) {

        // shortcut
        public String haveError(String message) {
            return statementAnalysis.messages.stream()
                    .filter(m -> m.message.contains(message))
                    .map(Message::toString)
                    .findFirst()
                    .orElse(null);
        }

        public int getProperty(Value value, VariableProperty variableProperty) {
            return evaluationContext.getProperty(value, variableProperty);
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
                    ", analysisStatus=" + analysisStatus +
                    '}';
        }
    }

    void visit(Data data);
}
