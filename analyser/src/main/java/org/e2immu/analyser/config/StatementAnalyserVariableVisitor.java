package org.e2immu.analyser.config;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;

import java.util.Map;

public interface StatementAnalyserVariableVisitor {
    void visit(int iteration, MethodInfo methodInfo, String statementId,
               String variableName, Variable variable, Value currentValue, Map<VariableProperty, Integer> properties);
}
