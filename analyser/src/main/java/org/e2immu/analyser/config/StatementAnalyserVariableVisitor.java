package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.Variable;

import java.util.Map;
import java.util.Objects;

public interface StatementAnalyserVariableVisitor {

    record Data(int iteration,
                EvaluationContext evaluationContext,
                MethodInfo methodInfo,
                String statementId,
                String variableName,
                Variable variable,
                Expression currentValue,
                boolean currentValueIsDelayed,
                VariableProperties properties,
                VariableInfo variableInfo,
                VariableInfoContainer variableInfoContainer) {

        public Data {
            Objects.requireNonNull(currentValue);
        }

        public int getProperty(VariableProperty variableProperty) {
            return properties.getOrDefault(variableProperty, Level.DELAY);
        }

        public boolean hasProperty(VariableProperty variableProperty) {
            return properties.isSet(variableProperty);
        }

        public int getPropertyOfCurrentValue(VariableProperty variableProperty) {
            return evaluationContext.getProperty(currentValue, variableProperty);
        }

        @Override
        public String toString() {
            return "Data{" +
                    "iteration=" + iteration +
                    ", methodInfo=" + methodInfo +
                    ", statementId='" + statementId + '\'' +
                    ", variableName='" + variableName + '\'' +
                    ", variable=" + variable +
                    ", currentValue=" + currentValue +
                    ", properties=" + properties +
                    ", evaluationContext=" + evaluationContext +
                    '}';
        }
    }

    void visit(Data data);
}
