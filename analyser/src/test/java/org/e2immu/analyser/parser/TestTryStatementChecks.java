package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.ConstantExpression;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.StringConcat;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestTryStatementChecks extends CommonTestRunner {
    private static final String METHOD1_FQN = ""; // TODO

    public TestTryStatementChecks() {
        super(false);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method3".equals(d.methodInfo().name) && "1.1.1".equals(d.statementId())) {
            Assert.assertEquals("s", d.haveError(Message.USELESS_ASSIGNMENT));
        }
        if ("method1".equals(d.methodInfo().name)) {
            if ("0.0.0".equals(d.statementId())) {
                Expression value0 = d.statementAnalysis().variables.get(METHOD1_FQN).current().getValue();
                Assert.assertTrue("Got " + value0.getClass(), value0 instanceof StringConcat);
            }
            if ("0.1.0".equals(d.statementId())) {
                Expression value1 = d.statementAnalysis().variables.get(METHOD1_FQN).current().getValue();
                Assert.assertTrue("Got " + value1.getClass(), value1 instanceof ConstantExpression);
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if (d.iteration() == 0 && "method1".equals(d.methodInfo().name)) {
            Expression srv = d.methodAnalysis().getSingleReturnValue();
            Assert.assertSame(EmptyExpression.RETURN_VALUE, srv);
        }
    };

    @Test
    public void test() throws IOException {
        testClass("TryStatementChecks", 1, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
