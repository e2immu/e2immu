package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
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
            return evaluationContext.getProperty(value, variableProperty);
        }

        public VariableInfo getFieldAsVariable(FieldInfo fieldInfo) {
            return methodAnalysis.getLastStatement().getLatestVariableInfo(fieldInfo.fullyQualifiedName());
        }

        public VariableInfo getReturnAsVariable() {
            return methodAnalysis.getLastStatement().getLatestVariableInfo(methodInfo.fullyQualifiedName());
        }

        public VariableInfo getThisAsVariable() {
            return methodAnalysis.getLastStatement().getLatestVariableInfo(methodInfo.typeInfo.fullyQualifiedName + ".this");
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
