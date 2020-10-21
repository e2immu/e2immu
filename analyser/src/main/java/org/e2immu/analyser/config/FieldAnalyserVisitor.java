package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.model.FieldAnalysis;
import org.e2immu.analyser.model.FieldInfo;

import java.util.Map;

public interface FieldAnalyserVisitor {
    void visit(Data data);

    record Data(int iteration, FieldInfo fieldInfo, FieldAnalysis fieldAnalysis, Map<String, AnalysisStatus> statuses) {
    }
}
