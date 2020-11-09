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

public class Test_04_ConditionalChecks extends CommonTestRunner {
    private static final String RETURN1 = "org.e2immu.analyser.testexample.ConditionalChecks.method1(boolean,boolean)";
    private static final String A1 = RETURN1 + ":0:a";
    private static final String B1 = RETURN1 + ":1:b";

    private static final String RETURN_1_VALUE = "(not (" + A1 + ") and " + B1 + ")?4:(" + A1 + " and not (" + B1 + "))?3:" +
            "(not (" + A1 + ") and not (" + B1 + "))?2:(" + A1 + " and " + B1 + ")?1:<return value>";

    private static final String RETURN3 = "org.e2immu.analyser.testexample.ConditionalChecks.method3(String,String)";
    private static final String A3 = RETURN3 + ":0:a";
    private static final String B3 = "org.e2immu.analyser.testexample.ConditionalChecks.method3(String,String):1:b";

    private static final String RETURN5 = "org.e2immu.analyser.testexample.ConditionalChecks.method5(Object)";
    private static final String O5 = RETURN5 + ":0:o";
    private static final String THIS_GET_CLASS = "org.e2immu.analyser.testexample.ConditionalChecks.this.getClass()";
    private static final String THIS = "org.e2immu.analyser.testexample.ConditionalChecks.this";
    private static final String O5_GET_CLASS = "org.e2immu.analyser.testexample.ConditionalChecks.method5(Object):0:o.getClass()";
    private static final String I = "org.e2immu.analyser.testexample.ConditionalChecks.i";
    private static final String CC_I = "org.e2immu.analyser.testexample.ConditionalChecks.i#" + O5;

    public Test_04_ConditionalChecks() {
        super(false);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("method5".equals(d.methodInfo().name)) {
            if (O5.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertFalse(d.hasProperty(VariableProperty.NOT_NULL));
                }
            }
            if ("conditionalChecks".equals(d.variableName())) {
                if ("2".equals(d.statementId())) {
                    Assert.assertEquals(O5, d.currentValue().toString());
                }
            }
            if ("3".equals(d.statementId())) {
                if (CC_I.equals(d.variableName())) {
                    String expectValue = d.iteration() == 0 ? UnknownValue.NO_VALUE.toString() : ""; // FIXME "" won't be correct
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                }
                if (RETURN5.equals(d.variableName())) {
                    Assert.assertEquals("(not (" + O5 + " == " + THIS + ") and (null == " + O5 + " or not (" + O5_GET_CLASS + " == " + THIS_GET_CLASS + ")))?false:" +
                            O5 + " == " + THIS + "?true:<return value>", d.currentValue().toString());
                    Assert.assertEquals(VariableInfoContainer.LEVEL_3_EVALUATION, d.variableInfoContainer().getCurrentLevel());
                }
            }
        }
        if (RETURN1.equals(d.variableName())) {
            // return 1;
            if ("0.0.0".equals(d.statementId())) {
                Assert.assertEquals("1", d.currentValue().toString());
                Assert.assertEquals("(" + A1 + " and " + B1 + ")", d.variableInfo().getStateOnAssignment().toString());
            }
            // after if(a&&b) return 1
            if ("0".equals(d.statementId())) {
                Assert.assertEquals("(" + A1 + " and " + B1 + ")?1:<return value>", d.currentValue().toString());
                Assert.assertSame(UnknownValue.EMPTY, d.variableInfo().getStateOnAssignment());
            }
            if ("1.0.0".equals(d.statementId())) {
                Assert.assertEquals("2", d.currentValue().toString());
                Assert.assertEquals("(not (" + A1 + ") and not (" + B1 + "))", d.variableInfo().getStateOnAssignment().toString());
            }
            // after if (!a && !b) return 2;
            if ("1".equals(d.statementId())) {
                Assert.assertSame(UnknownValue.EMPTY, d.variableInfo().getStateOnAssignment());
                // we do NOT expect a regression to the ReturnVariable
                Assert.assertEquals("(not (" + A1 + ") and not (" + B1 + "))?2:(" + A1 + " and " + B1 + ")?1:<return value>",
                        d.currentValue().toString());
            }
            if ("2".equals(d.statementId())) {
                Assert.assertSame(UnknownValue.EMPTY, d.variableInfo().getStateOnAssignment());
                // we do NOT expect a regression to the ReturnVariable
                Assert.assertEquals("(" + A1 + " and not (" + B1 + "))?3:" +
                                "(not (" + A1 + ") and not (" + B1 + "))?2:(" + A1 + " and " + B1 + ")?1:<return value>",
                        d.currentValue().toString());
            }
            if ("3".equals(d.statementId())) {
                Assert.assertSame(UnknownValue.EMPTY, d.variableInfo().getStateOnAssignment());
                // we do NOT expect a regression to the ReturnVariable
                Assert.assertEquals(RETURN_1_VALUE, d.currentValue().toString());
            }
            if ("4".equals(d.statementId())) {
                Assert.fail("not reached!");
            }
        }
        if (RETURN3.equals(d.variableName())) {
            if ("0".equals(d.statementId())) {
                Assert.assertEquals("<return value>", d.currentValue().toString());
            }
            if ("1".equals(d.statementId())) {
                Assert.assertEquals("<return value>", d.currentValue().toString());
            }
            if ("2".equals(d.statementId())) {
                Assert.assertEquals(A3 + " + " + B3, d.currentValue().toString());
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        FlowData.Execution inBlock = d.statementAnalysis().flowData.guaranteedToBeReachedInCurrentBlock.get();
        FlowData.Execution inMethod = d.statementAnalysis().flowData.guaranteedToBeReachedInMethod.get();
        Map<InterruptsFlow, FlowData.Execution> interruptsFlow = d.statementAnalysis().flowData.interruptsFlow.getOrElse(null);

        if ("method1".equals(d.methodInfo().name)) {

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
                Assert.assertEquals(UnknownValue.EMPTY.toString(), d.statementAnalysis().methodLevelData.combinedPrecondition.get().toString());
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
                Assert.assertEquals(UnknownValue.EMPTY.toString(), d.statementAnalysis().methodLevelData.combinedPrecondition.get().toString());
            }
            if ("2".equals(d.statementId())) {
                Assert.assertEquals("(" + A1 + " and not (" + B1 + "))", d.statementAnalysis().stateData.valueOfExpression.get().toString());
                Assert.assertEquals("(not (" + A1 + ") and " + B1 + ")", d.state().toString());
                Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inBlock);
                Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
            }
            // constant condition
            if ("3".equals(d.statementId())) {
                Assert.assertEquals("true", d.statementAnalysis().stateData.valueOfExpression.get().toString());
                Assert.assertEquals("false", d.state().toString()); // after the statement...
                Assert.assertEquals("ERROR in M:method1:3: Condition in 'if' or 'switch' statement evaluates to constant",
                        d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
                Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inBlock);
                Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                Assert.assertEquals(UnknownValue.EMPTY.toString(), d.statementAnalysis().methodLevelData.combinedPrecondition.get().toString());
            }
            // unreachable statement
            if ("4".equals(d.statementId())) {
                Assert.assertEquals(FlowData.Execution.NEVER, inBlock);
                Assert.assertEquals(FlowData.Execution.NEVER, inMethod);
                Assert.assertNotNull(d.haveError(Message.UNREACHABLE_STATEMENT));
                Assert.assertFalse(d.statementAnalysis().methodLevelData.combinedPrecondition.isSet());
            }
            if ("5".equals(d.statementId())) {
                Assert.assertEquals(FlowData.Execution.NEVER, inBlock);
                Assert.assertEquals(FlowData.Execution.NEVER, inMethod);
                Assert.assertNull(d.haveError(Message.UNREACHABLE_STATEMENT));
                VariableInfo ret = d.getReturnAsVariable();
                Assert.assertNull(ret); // unreachable statement, no data have even been copied!
                Assert.assertFalse(d.statementAnalysis().methodLevelData.combinedPrecondition.isSet());
            }

        }
        if ("method3".equals(d.methodInfo().name)) {
            if (d.iteration() == 0) {
                if ("0".equals(d.statementId())) {
                    Assert.assertSame(UnknownValue.EMPTY, d.condition());
                    Assert.assertEquals("not (null == " + A3 + ")", d.state().toString());
                    Assert.assertTrue(d.statementAnalysis().stateData.statementContributesToPrecondition.isSet());
                }
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("null == " + A3, d.condition().toString());
                    Assert.assertEquals("null == " + A3, d.state().toString());
                    Assert.assertTrue(d.haveSetProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL));
                    Assert.assertFalse(d.statementAnalysis().stateData.statementContributesToPrecondition.isSet());
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertSame(UnknownValue.EMPTY, d.condition());
                    Assert.assertEquals("(not (null == " + A3 + ") " + "and not (null == " + B3 + "))", d.state().toString());
                    Assert.assertTrue(d.statementAnalysis().stateData.statementContributesToPrecondition.isSet());
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
                AnalysisStatus expectStatus = d.iteration() == 0 ? AnalysisStatus.PROGRESS : AnalysisStatus.DONE;
                Assert.assertEquals(d.toString(), expectStatus, d.result().analysisStatus);
            } else {
                Assert.assertEquals("Statement " + d.statementId() + " it " + d.iteration(), AnalysisStatus.DONE, d.result().analysisStatus);
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
            Assert.assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
            Assert.assertEquals(RETURN_1_VALUE, d.methodAnalysis().getSingleReturnValue().toString());
        }
        if ("method5".equals(d.methodInfo().name)) {
            Assert.assertEquals(Level.DELAY, d.parameterAnalyses().get(0).getProperty(VariableProperty.NOT_NULL));
        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("method5".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                Assert.assertEquals(O5 + " == " + THIS, d.evaluationResult().value.toString());
            }
            if ("0.0.0".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                Assert.assertEquals("true", d.evaluationResult().value.toString());
            }
            if ("1".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
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
