package org.e2immu.analyser.config;

import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.StatementAnalysis;
import org.e2immu.analyser.model.Value;

public interface StatementAnalyserVisitor {

    class Data {
        public final int iteration;
        public final MethodInfo methodInfo;
        public final StatementAnalysis statementAnalysis;
        public final String statementId;
        public final Value condition;
        public final Value state;

        public Data(int iteration, MethodInfo methodInfo, StatementAnalysis statementAnalysis, String statementId, Value condition, Value state) {
            this.iteration = iteration;
            this.methodInfo = methodInfo;
            this.statementAnalysis = statementAnalysis;
            this.statementId = statementId;
            this.condition = condition;
            this.state = state;
        }
    }

    void visit(Data data);
}
