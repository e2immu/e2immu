package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
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

    // if(x) return a; return b;
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
                            d.statementAnalysis().stateData.getConditionManager().state().toString());
                }
            }
        };

        final String RETURN = "org.e2immu.analyser.testexample.IfStatementChecks_0.method1(String)";
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (RETURN.equals(d.variableName())) {
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

    // if(x) return a;else return b;
    @Test
    public void test1() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method2".equals(d.methodInfo().name)) {
                Expression value = d.methodAnalysis().getSingleReturnValue();
                Assert.assertTrue("Got: " + value.getClass(), value instanceof InlinedMethod);
            }
        };

        final String RETURN = "org.e2immu.analyser.testexample.IfStatementChecks_1.method2(String)";
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (RETURN.equals(d.variableName())) {
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("\"b\"", d.currentValue().toString());
                }
                if ("0.1.0".equals(d.statementId())) {
                    Assert.assertEquals("b", d.currentValue().toString());
                }
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("null==b?\"b\":b", d.currentValue().toString());
                }
            }
        };
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method2".equals(d.methodInfo().name) && "0.1.0".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                Assert.assertEquals("b", d.evaluationResult().value().toString());
            }
        };

        testClass("IfStatementChecks_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    // if(c!=null) return c; return "abc";
    @Test
    public void test2() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method3".equals(d.methodInfo().name)) {
                Expression value = d.methodAnalysis().getSingleReturnValue();
                Assert.assertTrue("Got: " + value.getClass(), value instanceof InlinedMethod);
            }
        };

        final String RETURN = "org.e2immu.analyser.testexample.IfStatementChecks_2.method3(String)";
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (RETURN.equals(d.variableName())) {
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("c", d.currentValue().toString());
                }
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("null==c?<return value>:c", d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("null==c?\"abc\":c", d.currentValue().toString());
                }
            }
        };

        testClass("IfStatementChecks_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test3() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if (d.iteration() > 0 && "method4".equals(d.methodInfo().name)) {
                Expression value = d.methodAnalysis().getSingleReturnValue();
                Assert.assertEquals("null==c?\"abc\":\"cef\"", value.toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.methodInfo().name.equals("method4") && "res".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertFalse(d.hasProperty(VariableProperty.MODIFIED));
                    Assert.assertFalse(d.variableInfo().isRead()); // nothing that points to not null
                } else if ("1.0.0".equals(d.statementId()) || "1.1.0".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfo().isAssigned());
                    Assert.assertFalse(d.variableInfo().isRead()); // nothing that points to not null
                } else if ("1".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfo().isAssigned());
                    Assert.assertFalse(d.variableInfo().isRead()); // nothing that points to not null
                } else if ("2".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfo().isRead()); // twice actually
                } else Assert.fail("Statement " + d.statementId());
            }
        };

        testClass("IfStatementChecks_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
