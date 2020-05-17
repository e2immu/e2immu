package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.MethodInfo;

public interface StatementAnalyserVisitor {
    void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement);
}
