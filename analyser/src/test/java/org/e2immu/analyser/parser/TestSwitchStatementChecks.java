package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestSwitchStatementChecks extends CommonTestRunner {
    public TestSwitchStatementChecks() {
        super(false);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("method7".equals(d.methodInfo.name) && "3".equals(d.statementId) && "res".equals(d.variableName)) {
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.properties.get(VariableProperty.NOT_NULL));
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method7".equals(d.methodInfo.name)) {
            if ("2".equals(d.statementId)) {
                Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
            }
            if ("2.2.0".equals(d.statementId)) {
                Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
            }
        }
        if ("method3".equals(d.methodInfo.name) && "0.2.0".equals(d.statementId)) {
            Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
        }
        if ("method3".equals(d.methodInfo.name) && "0.2.0.0.0".equals(d.statementId)) {
            Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
        }
    };


    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("method3".equals(d.methodInfo().name)) {
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL));
        }
        if ("method7".equals(d.methodInfo().name) && d.iteration() > 0) {
            // @Constant annotation missing, but is marked as constant
            Assert.assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.CONSTANT));
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("SwitchStatementChecks", 3, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
