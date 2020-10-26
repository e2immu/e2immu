package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.IncrementalMap;

import java.util.Map;

public interface StatementAnalyserVariableVisitor {

    class Data {
        public final int iteration;
        public final MethodInfo methodInfo;
        public final String statementId;
        public final String variableName;
        public final Variable variable;
        public final Value currentValue;
        public final Value stateOnAssignment;
        public final ObjectFlow objectFlow;
        public final IncrementalMap<VariableProperty> properties;
        public final EvaluationContext evaluationContext;

        public Data(int iteration,
                    EvaluationContext evaluationContext,
                    MethodInfo methodInfo, String statementId,
                    String variableName, Variable variable, Value currentValue, Value stateOnAssignment,
                    ObjectFlow objectFlow, IncrementalMap<VariableProperty> properties) {
            this.iteration = iteration;
            this.methodInfo = methodInfo;
            this.variable = variable;
            this.variableName = variableName;
            this.currentValue = currentValue;
            this.stateOnAssignment = stateOnAssignment;
            this.objectFlow = objectFlow;
            this.properties = properties;
            this.statementId = statementId;
            this.evaluationContext = evaluationContext;
        }

        public int getProperty(VariableProperty variableProperty) {
            return properties.getOrDefault(variableProperty, Level.DELAY);
        }

        public int getPropertyOfCurrentValue(VariableProperty variableProperty) {
            return evaluationContext.getProperty(currentValue, variableProperty);
        }
    }

    void visit(Data data);
}
