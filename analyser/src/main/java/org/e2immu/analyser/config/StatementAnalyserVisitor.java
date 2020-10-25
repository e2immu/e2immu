package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.StatementAnalysis;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.parser.Message;

import java.util.Map;

public interface StatementAnalyserVisitor {

    class Data {
        public final int iteration;
        public final MethodInfo methodInfo;
        public final StatementAnalysis statementAnalysis;
        public final String statementId;
        public final Value condition;
        public final Value state;
        public final Map<String, AnalysisStatus> statusesAsMap;

        public Data(int iteration, MethodInfo methodInfo, StatementAnalysis statementAnalysis,
                    String statementId, Value condition, Value state, Map<String, AnalysisStatus> statusesAsMap) {
            this.iteration = iteration;
            this.methodInfo = methodInfo;
            this.statementAnalysis = statementAnalysis;
            this.statementId = statementId;
            this.condition = condition;
            this.state = state;
            this.statusesAsMap = statusesAsMap;
        }

        // shortcut
        public String haveError(String message) {
            return statementAnalysis.messages.stream()
                    .filter(m -> m.message.contains(message))
                    .map(Message::toString)
                    .findFirst()
                    .orElse(null);
        }
    }

    void visit(Data data);
}
