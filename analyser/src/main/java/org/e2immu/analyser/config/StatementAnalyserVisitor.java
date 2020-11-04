package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.StatementAnalyserResult;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Message;

import java.util.Map;

public interface StatementAnalyserVisitor {

    record Data(StatementAnalyserResult result,
                int iteration,
                EvaluationContext evaluationContext,
                MethodInfo methodInfo, StatementAnalysis statementAnalysis,
                String statementId, Value condition, Value state, Map<String, AnalysisStatus> statusesAsMap,
                boolean ignoreErrorsOnCondition) {

        public VariableInfo getFieldAsVariable(FieldInfo fieldInfo) {
            return statementAnalysis.variables.get(fieldInfo.fullyQualifiedName());
        }

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
                    ", result=" + result +
                    '}';
        }

        public boolean haveSetProperty(VariableProperty variableProperty, int value) {
            return result.getModifications().anyMatch(m -> m instanceof AbstractAnalysisBuilder.SetProperty setProperty &&
                    setProperty.value == value && setProperty.variableProperty == variableProperty);
        }

        public VariableInfo getReturnAsVariable() {
            return statementAnalysis.variables.get(methodInfo.fullyQualifiedName());
        }

        public VariableInfo getThisAsVariable() {
            return statementAnalysis.variables.get(methodInfo.typeInfo.fullyQualifiedName() + ".this");
        }
    }

    void visit(Data data);
}
