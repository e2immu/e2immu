package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.Variable;

import java.util.Map;

public interface StatementAnalyserVariableVisitor {

    record Data(int iteration,
                EvaluationContext evaluationContext,
                MethodInfo methodInfo,
                String statementId,
                String variableName,
                Variable variable,
                Value currentValue,
                Map<VariableProperty, Integer> properties,
                VariableInfo variableInfo,
                VariableInfoContainer variableInfoContainer) {

        public int getProperty(VariableProperty variableProperty) {
            return properties.getOrDefault(variableProperty, Level.DELAY);
        }

        public boolean hasProperty(VariableProperty variableProperty) {
            return properties.containsKey(variableProperty) && Level.DELAY != properties.get(variableProperty);
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
