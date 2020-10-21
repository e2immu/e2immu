package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.Level;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestNotModifiedChecks2 extends CommonTestRunner {
    public TestNotModifiedChecks2() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("add".equals(d.methodInfo.name) && "theSet".equals(d.variableName)) {
            if ("1".equals(d.statementId)) {
                Assert.assertEquals(Level.FALSE, d.properties.get(VariableProperty.MODIFIED));
            }
            if ("2".equals(d.statementId)) {
                Assert.assertEquals(Level.TRUE, d.properties.get(VariableProperty.MODIFIED));
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("NotModifiedChecks2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
