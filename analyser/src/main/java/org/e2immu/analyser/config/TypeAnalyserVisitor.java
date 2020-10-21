package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.model.TypeAnalysis;
import org.e2immu.analyser.model.TypeInfo;

import java.util.Map;

public interface TypeAnalyserVisitor {
    void visit(Data data);

    record Data(int iteration, TypeInfo typeInfo, TypeAnalysis typeAnalysis, Map<String, AnalysisStatus> statuses) {
    }
}
