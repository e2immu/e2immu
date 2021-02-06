package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.FlowData;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_30_SwitchStatement extends CommonTestRunner {
    public Test_30_SwitchStatement() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        testClass("SwitchStatement_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("SwitchStatement_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "0.0.2".equals(d.statementId())) {
                // this point comes before we check the value against the condition manager
                Assert.assertEquals("'a'!=c&&'b'!=c", d.evaluationResult()
                        .evaluationContext().getConditionManager().condition().toString());
                Assert.assertEquals("'a'==c||'b'==c", d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "0.0.2".equals(d.statementId())) {
                Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL));
            }
        };

        testClass("SwitchStatement_2", 2, 1, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                Assert.assertEquals("b", d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("SwitchStatement_3", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        testClass("SwitchStatement_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_5() throws IOException {
        testClass("SwitchStatement_5", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_6() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "3".equals(d.statementId()) && "res".equals(d.variableName())) {
                Assert.assertEquals("nullable? instance type String", d.currentValue().toString());
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    Assert.assertNotNull(d.haveError(Message.TRIVIAL_CASES_IN_SWITCH));
                    Assert.assertSame(FlowData.Execution.NEVER, d.statementAnalysis().flowData.interruptStatus());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && d.iteration() > 0) {
                Assert.assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.CONSTANT));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL));
            }
        };

        testClass("SwitchStatement_6", 1, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_7() throws IOException {
        testClass("SwitchStatement_7", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
