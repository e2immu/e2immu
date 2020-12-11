package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_11_IfStatementChecks extends CommonTestRunner {
    public Test_11_IfStatementChecks() {
        super(false);
    }

    @Test
    public void test0() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                Expression value = d.methodAnalysis().getSingleReturnValue();
                Assert.assertTrue("Got: " + value.getClass(), value instanceof InlinedMethod);
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("null!=a",
                            d.statementAnalysis().stateData.conditionManager.get().state.toString());
                }
            }
        };

        final String RETURN = "org.e2immu.analyser.testexample.IfStatementChecks_0.method1(String)";
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method1".equals(d.methodInfo().name) && RETURN.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("null==a?\"b\":<return value>", d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("null==a?\"b\":a", d.currentValue().toString());
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
                }
            }
        };

        testClass("IfStatementChecks_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test1() throws IOException {

        // inlining happens when the replacements are active
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method2".equals(d.methodInfo().name)) {
                Expression value = d.methodAnalysis().getSingleReturnValue();
                Assert.assertTrue("Got: " + value.getClass(), value instanceof InlinedMethod);
            }
        };
        testClass("IfStatementChecks_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test2() throws IOException {

        // inlining happens when the replacements are active
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method3".equals(d.methodInfo().name)) {
                Expression value = d.methodAnalysis().getSingleReturnValue();
                Assert.assertTrue("Got: " + value.getClass(), value instanceof InlinedMethod);
            }
        };

        testClass("IfStatementChecks_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test3() throws IOException {

        // inlining happens when the replacements are active
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if (d.iteration() > 0 && "method4".equals(d.methodInfo().name)) {
                Expression value = d.methodAnalysis().getSingleReturnValue();
                // with more transformations, we can make this into an inline value TODO
                Assert.assertEquals("<return value>", value.toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.methodInfo().name.equals("method4") && "res".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertFalse(d.hasProperty(VariableProperty.MODIFIED));
                    Assert.assertFalse(d.hasProperty(VariableProperty.READ)); // nothing that points to not null
                } else if ("1.0.0".equals(d.statementId()) || "1.1.0".equals(d.statementId())) {
                    Assert.assertEquals(1, d.getProperty(VariableProperty.ASSIGNED));
                    Assert.assertFalse(d.hasProperty(VariableProperty.READ)); // nothing that points to not null
                } else if ("1".equals(d.statementId())) {
                    Assert.assertEquals(1, d.getProperty(VariableProperty.ASSIGNED));
                    Assert.assertFalse(d.hasProperty(VariableProperty.READ)); // nothing that points to not null
                } else if ("2".equals(d.statementId())) {
                    Assert.assertEquals(2, d.getProperty(VariableProperty.READ));
                } else Assert.fail("Statement " + d.statementId());
            }
        };


        testClass("IfStatementChecks_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
