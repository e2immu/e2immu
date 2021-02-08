package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
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
        final String METHOD_FQN = TYPE + ".method(java.lang.String)";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && METHOD_FQN.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    // meaning: no idea
                    Assert.assertEquals("nullable instance type String", d.currentValue().toString());
                }
            }
        };
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
                Assert.assertTrue("Statement " + d.statementId() + ", it " + d.iteration(),
                        d.statementAnalysis().methodLevelData.internalObjectFlows.isFrozen());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                Expression srv = d.methodAnalysis().getSingleReturnValue();
                Assert.assertEquals("nullable instance type String", srv.toString());
            }
        };

        testClass("TryStatement_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
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
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "npe".equals(d.variableName())) {
                if ("1.1.0".equals(d.statementId())) {
                    Assert.assertEquals("instance type NullPointerException", d.currentValue().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "1.1.1".equals(d.statementId())) {
                Assert.assertEquals("ERROR in M:method:1.1.1: Useless assignment: res",
                        d.haveError(Message.USELESS_ASSIGNMENT));
            }
            if ("method".equals(d.methodInfo().name) && "1.2.1".equals(d.statementId())) {
                Assert.assertEquals("ERROR in M:method:1.2.1: Useless assignment: res",
                        d.haveError(Message.USELESS_ASSIGNMENT));
            }
        };

        // warn: unused parameter
        testClass("TryStatement_2", 2, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "res".equals(d.variableName())) {
                if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                    Assert.assertEquals("null", d.currentValue().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                Assert.assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.CONSTANT));
            }
        };

        testClass("TryStatement_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        testClass("TryStatement_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
