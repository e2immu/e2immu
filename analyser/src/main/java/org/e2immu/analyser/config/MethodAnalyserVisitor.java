package org.e2immu.analyser.config;

import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterAnalysis;

import java.util.List;

public interface MethodAnalyserVisitor {
    void visit(Data data);

    record Data(int iteration, MethodInfo methodInfo, MethodAnalysis methodAnalysis,
                List<ParameterAnalysis> parameterAnalyses) {
    }
}
