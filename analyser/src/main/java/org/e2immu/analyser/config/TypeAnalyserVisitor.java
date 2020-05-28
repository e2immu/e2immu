package org.e2immu.analyser.config;

import org.e2immu.analyser.model.TypeInfo;

public interface TypeAnalyserVisitor {
    void visit(int iteration, TypeInfo typeInfo);
}
