package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_11_IfStatement extends CommonTestRunner {
    public Test_11_IfStatement() {
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

        final String RETURN = "org.e2immu.analyser.testexample.IfStatement_0.method1(String)";
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

        testClass("IfStatement_0", 0, 0, new DebugConfiguration.Builder()
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

        final String RETURN = "org.e2immu.analyser.testexample.IfStatement_1.method2(String)";
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
                Assert.assertEquals("b", d.evaluationResult().value().toString());
            }
        };

        testClass("IfStatement_1", 0, 0, new DebugConfiguration.Builder()
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

        final String RETURN = "org.e2immu.analyser.testexample.IfStatement_2.method3(String)";
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

        testClass("IfStatement_2", 0, 0, new DebugConfiguration.Builder()
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
                    Assert.assertFalse(d.variableInfo().isRead());
                } else if ("1.0.0".equals(d.statementId()) || "1.1.0".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfo().isAssigned());
                    Assert.assertFalse(d.variableInfo().isRead());
                } else if ("1".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfo().isAssigned());
                    Assert.assertFalse(d.variableInfo().isRead());
                } else if ("2".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfo().isRead()); // twice actually
                } else Assert.fail("Statement " + d.statementId());
            }
        };

        testClass("IfStatement_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    /*
    Linked variables come later in get2 and get3 as compared to get1.
    Should we be worried about this?
     */

    @Test
    public void test4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("get3".equals(d.methodInfo().name) && "i3".equals(d.variableName())) {
                String expectValue = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "map.get(label3)";
                Assert.assertEquals(expectValue, d.currentValue().toString());
                String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get1".equals(d.methodInfo().name) && d.iteration() > 0) {
                Assert.assertEquals("null==map.get(label1)?defaultValue1:map.get(label1)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get2".equals(d.methodInfo().name) && d.iteration() >= 2) {
                Assert.assertEquals("null==map.get(label2)?defaultValue2:map.get(label2)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get3".equals(d.methodInfo().name) && d.iteration() >= 2) {
                Assert.assertEquals("null==map.get(label3)?defaultValue3:map.get(label3)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("IfStatement_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("get3".equals(d.methodInfo().name) && "i3".equals(d.variableName())) {
                String expectValue = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "map.get(label3)";
                Assert.assertEquals(expectValue, d.currentValue().toString());
                String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get1".equals(d.methodInfo().name) && d.iteration() > 0) {
                Assert.assertEquals("null==map.get(label1)?defaultValue1:map.get(label1)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get2".equals(d.methodInfo().name) && d.iteration() >= 2) {
                Assert.assertEquals("null==map.get(label2)?defaultValue2:map.get(label2)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get3".equals(d.methodInfo().name) && d.iteration() >= 2) {
                Assert.assertEquals("null==map.get(label3)?defaultValue3:map.get(label3)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("IfStatement_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
