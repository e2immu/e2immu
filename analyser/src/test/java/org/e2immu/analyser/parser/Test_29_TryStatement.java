package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.expression.ConstantExpression;
import org.e2immu.analyser.model.expression.StringConcat;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_29_TryStatement extends CommonTestRunner {

    public Test_29_TryStatement() {
        super(false);
    }


    @Test
    public void test_0() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.TryStatement_0";
        final String METHOD_FQN = TYPE + ".method(String)";

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    Expression value0 = d.statementAnalysis().variables.get(METHOD_FQN).current().getValue();
                    Assert.assertTrue("Got " + value0.getClass(), value0 instanceof StringConcat);
                }
                if ("0.1.0".equals(d.statementId())) {
                    Expression value1 = d.statementAnalysis().variables.get(METHOD_FQN).current().getValue();
                    Assert.assertTrue("Got " + value1.getClass(), value1 instanceof ConstantExpression);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                Expression srv = d.methodAnalysis().getSingleReturnValue();
                Assert.assertNull(srv); // multiple exit points
            }
        };

        testClass("TryStatement_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("TryStatement_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_2() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "1.1.1".equals(d.statementId())) {
                Assert.assertEquals("s", d.haveError(Message.USELESS_ASSIGNMENT));
            }
        };
        testClass("TryStatement_2", 1, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                Assert.assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.CONSTANT));
            }
        };

        testClass("TryStatement_3", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        testClass("TryStatement_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
