package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.FieldAnalysis;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Value;

import java.util.Map;

public interface FieldAnalyserVisitor {
    void visit(Data data);

    record Data(int iteration,
                EvaluationContext evaluationContext,
                FieldInfo fieldInfo, FieldAnalysis fieldAnalysis, Map<String, AnalysisStatus> statuses) {

        public int getProperty(Value value, VariableProperty variableProperty) {
            return evaluationContext.getProperty(value, variableProperty);
        }

    }
}
