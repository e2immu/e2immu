package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

public class TestNotModified1Checks extends CommonTestRunner {

    public TestNotModified1Checks() {
        super(true);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {

        // checks the 2 errors
        if ("useApply".equals(d.methodInfo.name) && Set.of("2", "3").contains(d.statementId)) {
            Assert.assertTrue(d.statementAnalysis.errorFlags.errorValue.isSet());
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if("apply".equals(d.methodInfo.name) && "consumer".equals(d.variableName)) {
            Assert.assertEquals(Level.TRUE, d.properties.get(VariableProperty.NOT_MODIFIED_1));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("NotModified1Checks", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
