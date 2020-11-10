package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_11_EvaluationErrors extends CommonTestRunner {
    public Test_11_EvaluationErrors() {
        super(false);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("testDivisionByZero".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementId())) {
                Assert.assertNotNull(d.haveError(Message.DIVISION_BY_ZERO));
            }
            if ("2".equals(d.statementId())) {
                Assert.assertNull(d.haveError(Message.DIVISION_BY_ZERO));
            }
        }
        if ("testDeadCode".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementId())) {
                Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
                Assert.assertNotNull(d.haveError(Message.UNREACHABLE_STATEMENT)); // copied up
            }
            // this one does not render a dead-code error, because its parent already has an error raised
            if ("1.0.0".equals(d.statementId())) {
               Assert.assertNotNull(d.haveError(Message.UNREACHABLE_STATEMENT));
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("EvaluationErrors", 3, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

}
