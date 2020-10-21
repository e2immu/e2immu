package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterAnalysis;

import java.util.List;
import java.util.Map;

public interface MethodAnalyserVisitor {
    void visit(Data data);

    record Data(int iteration, MethodInfo methodInfo, MethodAnalysis methodAnalysis,
                List<ParameterAnalysis> parameterAnalyses,  Map<String, AnalysisStatus> statuses) {
    }
}
