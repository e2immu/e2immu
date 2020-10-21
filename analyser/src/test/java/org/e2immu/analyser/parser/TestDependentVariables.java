package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.Level;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestDependentVariables extends CommonTestRunner {
    public TestDependentVariables() {
        super(false);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("method1".equals(d.methodInfo.name)) {

            int read = d.properties.getOrDefault(VariableProperty.READ, Level.DELAY);
            int assigned = d.properties.getOrDefault(VariableProperty.ASSIGNED, Level.DELAY);

            if ("2".equals(d.statementId) && "array[0]".equals(d.variableName)) {
                Assert.assertTrue(assigned > read);
            }
            if ("4".equals(d.statementId) && "array[0]".equals(d.variableName)) {
                Assert.assertTrue(assigned < read);
            }
            if ("4".equals(d.statementId) && "array".equals(d.variableName)) {
                Assert.assertTrue(read > 1);
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("DependentVariables", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
