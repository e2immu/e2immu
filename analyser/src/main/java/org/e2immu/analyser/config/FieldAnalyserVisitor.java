package org.e2immu.analyser.config;

import org.e2immu.analyser.model.FieldInfo;

public interface FieldAnalyserVisitor {
    void visit(int iteration, FieldInfo fieldInfo);
}
