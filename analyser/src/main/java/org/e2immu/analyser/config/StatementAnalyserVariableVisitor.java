package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;

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
        public final Map<VariableProperty, Integer> properties;

        public Data(int iteration, MethodInfo methodInfo, String statementId,
                    String variableName, Variable variable, Value currentValue, Value stateOnAssignment,
                    ObjectFlow objectFlow, Map<VariableProperty, Integer> properties) {
            this.iteration = iteration;
            this.methodInfo = methodInfo;
            this.variable = variable;
            this.variableName = variableName;
            this.currentValue = currentValue;
            this.stateOnAssignment = stateOnAssignment;
            this.objectFlow = objectFlow;
            this.properties = properties;
            this.statementId = statementId;
        }
    }

    void visit(Data data);
}
