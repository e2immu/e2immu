package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.CompanionAnalysis;
import org.e2immu.analyser.model.CompanionMethodName;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.model.MethodInfo;

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
