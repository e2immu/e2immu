package org.e2immu.analyser.config;

import org.e2immu.analyser.model.MethodInfo;

public interface MethodAnalyserVisitor {
    void visit(int iteration, MethodInfo methodInfo);
}
