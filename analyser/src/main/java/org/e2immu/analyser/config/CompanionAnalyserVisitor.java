package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.CompanionAnalysis;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;

import java.util.List;
import java.util.Map;

public interface CompanionAnalyserVisitor {
    void visit(Data data);

    record Data(int iteration,
                AnalysisStatus analysisStatus,
                EvaluationContext evaluationContext,
                EvaluationResult evaluationResult,
                MethodInfo mainMethod,
                CompanionMethodName companionMethodName,
                MethodInfo companionMethod,
                CompanionAnalysis companionAnalysis) {

    }

}
