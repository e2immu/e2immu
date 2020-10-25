package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.AnalyserConfiguration;
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
                Assert.assertNotNull(d.haveError(Message.DIVISION_BY_ZERO));
            }
            if ("1.0.0".equals(d.statementId)) {
                Assert.assertNotNull(d.haveError(Message.DIVISION_BY_ZERO));
            }
            if ("2".equals(d.statementId)) {
                Assert.assertNull(d.haveError(Message.DIVISION_BY_ZERO));
            }
        }
        if ("testDeadCode".equals(d.methodInfo.name)) {
            if ("1".equals(d.statementId)) {
                Assert.assertNotNull(d.haveError(Message.UNREACHABLE_STATEMENT));
            }
            // this one does not render a dead-code error, because its parent already has an error raised
            if ("1.0.0".equals(d.statementId)) {
                Assert.assertNull(d.haveError(Message.UNREACHABLE_STATEMENT));
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("EvaluationErrors", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

}
