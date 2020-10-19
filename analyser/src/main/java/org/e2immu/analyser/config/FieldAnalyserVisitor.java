package org.e2immu.analyser.config;

import org.e2immu.analyser.model.FieldAnalysis;
import org.e2immu.analyser.model.FieldInfo;

public interface FieldAnalyserVisitor {
    void visit(Data data);

    record Data(int iteration, FieldInfo fieldInfo, FieldAnalysis fieldAnalysis) {
    }
}
