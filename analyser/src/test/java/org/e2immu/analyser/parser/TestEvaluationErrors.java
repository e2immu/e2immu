package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestEvaluationErrors extends CommonTestRunner {
    public TestEvaluationErrors() {
        super(false);
    }

    /*
     public static int testDivisionByZero() {
        int i=0;
        int j = 23 / i;
        return j;
    }

    public static int testDeadCode() {
        int i=1;
        if(i != 1) {
            return 2;
        }
        return 3;
    }
     */
    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("testDivisionByZero".equals(d.methodInfo.name)) {
            if ("1".equals(d.statementId)) {
                Assert.assertTrue(d.numberedStatement.errorValue.get());
            }
            if ("1.0.0".equals(d.statementId)) {
                Assert.assertTrue(d.numberedStatement.errorValue.get());
            }
            if ("2".equals(d.statementId)) {
                Assert.assertFalse(d.numberedStatement.errorValue.isSet());
            }
        }
        if ("testDeadCode".equals(d.methodInfo.name)) {
            if ("1".equals(d.statementId)) {
                Assert.assertTrue(d.numberedStatement.errorValue.get());
            }
            // this one does not render a dead-code error, because its parent already has an error raised
            if ("1.0.0".equals(d.statementId)) {
                Assert.assertTrue(d.numberedStatement.inErrorState());
                Assert.assertFalse(d.numberedStatement.errorValue.isSet());
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("EvaluationErrors", 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
