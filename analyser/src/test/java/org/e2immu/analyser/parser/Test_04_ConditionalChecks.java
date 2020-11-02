package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

public class Test_04_ConditionalChecks extends CommonTestRunner {
    private static final String A1 = "org.e2immu.analyser.testexample.ConditionalChecks.method1(boolean,boolean):0:a";
    private static final String B1 = "org.e2immu.analyser.testexample.ConditionalChecks.method1(boolean,boolean):1:b";
    private static final String A3 = "org.e2immu.analyser.testexample.ConditionalChecks.method3(String,String):0:a";
    private static final String B3 = "org.e2immu.analyser.testexample.ConditionalChecks.method3(String,String):1:b";
    private static final String O5 = "org.e2immu.analyser.testexample.ConditionalChecks.method5(Object):0:o";
    private static final String THIS_GET_CLASS = "org.e2immu.analyser.testexample.ConditionalChecks.this.getClass()";
    private static final String THIS = "org.e2immu.analyser.testexample.ConditionalChecks.this";
    private static final String O5_GET_CLASS = "org.e2immu.analyser.testexample.ConditionalChecks.method5(Object):0:o.getClass()";
    private static final String I = "org.e2immu.analyser.testexample.ConditionalChecks.i";
    private static final String CC_I = "org.e2immu.analyser.testexample.ConditionalChecks.i#conditionalChecks";

    public Test_04_ConditionalChecks() {
        super(false);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("method5".equals(d.methodInfo().name) && O5.equals(d.variableName())) {
            if ("0".equals(d.statementId())) {
                Assert.assertFalse(d.properties().isSet(VariableProperty.NOT_NULL));
            }
        }
        if ("method5".equals(d.methodInfo().name) && "conditionalChecks".equals(d.variableName())) {
            if ("2".equals(d.statementId())) {
                Assert.assertEquals(O5, d.currentValue().toString());
            }
        }
        if ("method5".equals(d.methodInfo().name) && "3".equals(d.statementId()) && CC_I.equals(d.variableName())) {
            String expectValue = d.iteration() == 0 ? UnknownValue.NO_VALUE.toString() : "";
            Assert.assertEquals(expectValue, d.currentValue().toString());
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        FlowData.Execution inBlock = d.statementAnalysis().flowData.guaranteedToBeReachedInCurrentBlock.get();
        FlowData.Execution inMethod = d.statementAnalysis().flowData.guaranteedToBeReachedInMethod.get();
        Map<InterruptsFlow, FlowData.Execution> interruptsFlow = d.statementAnalysis().flowData.interruptsFlow.get();

        if ("method1".equals(d.methodInfo().name)) {
            String allReturnStatementSummaryKeys = d.statementAnalysis().methodLevelData.returnStatementSummaries.stream()
                    .map(Map.Entry::getKey).sorted().collect(Collectors.joining(", "));

            if ("0.0.0".equals(d.statementId())) {
                Assert.assertEquals("(" + A1 + " and " + B1 + ")", d.condition().toString());
                Assert.assertEquals("(" + A1 + " and " + B1 + ")", d.state().toString());
                Assert.assertEquals(FlowData.Execution.ALWAYS, inBlock);
                Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                Assert.assertEquals(Map.of(InterruptsFlow.RETURN, FlowData.Execution.ALWAYS), interruptsFlow);
            }
            if ("0".equals(d.statementId())) {
                Assert.assertSame(UnknownValue.EMPTY, d.condition());
                Assert.assertEquals("(not (" + A1 + ") or not (" + B1 + "))", d.state().toString());
                Assert.assertEquals(FlowData.Execution.ALWAYS, inBlock);
                Assert.assertEquals(FlowData.Execution.ALWAYS, inMethod);
                Assert.assertEquals(Map.of(InterruptsFlow.RETURN, FlowData.Execution.CONDITIONALLY), interruptsFlow);
                Assert.assertEquals("0.0.0", allReturnStatementSummaryKeys);

            }
            if ("1.0.0".equals(d.statementId())) {
                Assert.assertEquals("(not (" + A1 + ") and not (" + B1 + "))", d.condition().toString());
                Assert.assertEquals("(not (" + A1 + ") and not (" + B1 + "))", d.state().toString());
                Assert.assertEquals(FlowData.Execution.ALWAYS, inBlock);
                Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
            }
            if ("1".equals(d.statementId())) {
                Assert.assertEquals("((" + A1 + " or " + B1 + ") and (not (" + A1 + ") or not (" + B1 + ")))", d.state().toString());
                Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inBlock);
                Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                Assert.assertEquals("0.0.0, 1.0.0", allReturnStatementSummaryKeys);
            }
            if ("2".equals(d.statementId())) {
                Assert.assertEquals("(not (" + A1 + ") and " + B1 + ")", d.state().toString());
                Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inBlock);
                Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                Assert.assertEquals("0.0.0, 1.0.0, 2.0.0", allReturnStatementSummaryKeys);
            }
            // constant condition
            if ("3".equals(d.statementId())) {
                Assert.assertEquals(d.evaluationContext().boolValueFalse(), d.state());
                Assert.assertEquals("ERROR in M:method1:3: Condition in 'if' or 'switch' statement evaluates to constant",
                        d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
                Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inBlock);
                Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                Assert.assertEquals("0.0.0, 1.0.0, 2.0.0, 3.0.0", allReturnStatementSummaryKeys);
            }
            // unreachable statement
            if ("4".equals(d.statementId())) {
                Assert.assertEquals(FlowData.Execution.NEVER, inBlock);
                Assert.assertEquals(FlowData.Execution.NEVER, inMethod);
                Assert.assertNotNull(d.haveError(Message.UNREACHABLE_STATEMENT));
                Assert.assertEquals("0.0.0, 1.0.0, 2.0.0, 3.0.0, 4", allReturnStatementSummaryKeys);
            }
        }
        if ("method3".equals(d.methodInfo().name)) {
            if (d.iteration() == 0) {
                if ("0".equals(d.statementId())) {
                    Assert.assertSame(UnknownValue.EMPTY, d.condition());
                    Assert.assertEquals("not (null == " + A3 + ")", d.state().toString());
                }
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("null == " + A3, d.condition().toString());
                    Assert.assertEquals("null == " + A3, d.state().toString());
                    Assert.assertTrue(d.haveSetProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL));

                }
                if ("1".equals(d.statementId())) {
                    Assert.assertSame(UnknownValue.EMPTY, d.condition());
                    Assert.assertEquals("(not (null == " + A3 + ") " + "and not (null == " + B3 + "))", d.state().toString());
                }
            }
        }
        if ("method5".equals(d.methodInfo().name)) {
            // the escape mechanism does NOT kick in!
            if ("0".equals(d.statementId())) {
                Assert.assertEquals("not (" + O5 + " == " + THIS + ")", d.state().toString());
            } else if ("0.0.0".equals(d.statementId())) {
                Assert.assertEquals(O5 + " == " + THIS, d.state().toString());
            } else if ("1.0.0".equals(d.statementId())) {
                Assert.assertEquals("(not (" + O5 + " == " + THIS + ") and (null == " + O5 + " or not (" + O5_GET_CLASS + " == " + THIS_GET_CLASS + ")))", d.state().toString());
            } else {
                Assert.assertEquals("(not (null == " + O5 + ") and " + O5_GET_CLASS + " == " + THIS_GET_CLASS + " and not (" + O5 + " == " + THIS + "))", d.state().toString());
            }
            if ("3".equals(d.statementId())) {
                Assert.assertEquals(d.iteration() <= 1 ? AnalysisStatus.PROGRESS : AnalysisStatus.DONE, d.result().analysisStatus);
            } else {
                Assert.assertEquals(AnalysisStatus.DONE, d.result().analysisStatus);
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("method3".equals(d.methodInfo().name)) {
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.parameterAnalyses().get(0).getProperty(VariableProperty.NOT_NULL));
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.parameterAnalyses().get(1).getProperty(VariableProperty.NOT_NULL));
        }
        if ("method1".equals(d.methodInfo().name)) {
            Assert.assertSame(UnknownValue.EMPTY, d.methodAnalysis().getPrecondition());
            Assert.assertSame(UnknownValue.RETURN_VALUE, d.methodAnalysis().getSingleReturnValue());
        }
        if ("method5".equals(d.methodInfo().name)) {
            Assert.assertEquals(Level.DELAY, d.parameterAnalyses().get(0).getProperty(VariableProperty.NOT_NULL));
        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("method5".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_4, d.step());
                Assert.assertEquals("(null == " + O5 + " or not (" + O5_GET_CLASS + " == " + THIS_GET_CLASS + "))", d.evaluationResult().value.toString());
                Assert.assertTrue(d.evaluationResult().getModificationStream().count() > 0);
                Assert.assertTrue(d.haveMarkRead(O5));
                Assert.assertFalse(d.haveSetProperty(O5, VariableProperty.NOT_NULL));
            }
            if ("2".equals(d.statementId())) {
                Assert.assertFalse(d.haveSetProperty(O5, VariableProperty.NOT_NULL));
                Assert.assertTrue(d.haveMarkAssigned("conditionalChecks"));
                Assert.assertEquals(O5, d.evaluationResult().value.toString());
            }
            if ("3".equals(d.statementId())) {
                // there will be two iterations, in the second one, i will not have value "NO_VALUE" anymore
                String expectValueString = d.iteration() == 0 ? UnknownValue.NO_VALUE.toString() : I + " == " + I + "#" + O5;
                Assert.assertEquals(expectValueString, d.evaluationResult().value.toString());
                if (d.iteration() == 0) {
                    // markRead is only done in the first iteration
                    Assert.assertTrue(d.haveMarkRead("conditionalChecks"));
                    Assert.assertTrue(d.haveMarkRead(I));
                    Assert.assertTrue(d.haveMarkRead(I + "#" + O5));
                }
                Assert.assertFalse(d.haveSetProperty(O5, VariableProperty.NOT_NULL));
                Assert.assertFalse(d.haveSetProperty("conditionalChecks", VariableProperty.NOT_NULL));
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("ConditionalChecks", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

}
