package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.FieldAnalysis;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.parser.Message;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface FieldAnalyserVisitor {
    void visit(Data data);

    record Data(int iteration,
                EvaluationContext evaluationContext,
                FieldInfo fieldInfo,
                FieldAnalysis fieldAnalysis,
                Supplier<Stream<Message>> messageStream,
                Map<String, AnalysisStatus> statuses) {

        public int getProperty(Expression value, VariableProperty variableProperty) {
            return evaluationContext.getProperty(value, variableProperty, false);
        }

        public String haveError(String message) {
            return messageStream.get()
                    .filter(m -> m.message.contains(message))
                    .map(Message::toString)
                    .findFirst()
                    .orElse(null);
        }
    }
}
