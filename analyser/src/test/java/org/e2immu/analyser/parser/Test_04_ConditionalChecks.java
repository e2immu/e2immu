package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class Test_04_ConditionalChecks extends CommonTestRunner {

    public static final String CONDITIONAL_CHECKS = "conditionalChecks";

    public Test_04_ConditionalChecks() {
        super(false);
    }

    @Test
    public void test0() throws IOException {
        final String RETURN1 = "org.e2immu.analyser.testexample.ConditionalChecks.method1(boolean,boolean)";
        final String A1 = RETURN1 + ":0:a";
        final String B1 = RETURN1 + ":1:b";

        final String RETURN_1_VALUE = "!a&&b?4:a&&!b?3:!a&&!b?2:a&&b?1:<return value>";

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            FlowData.Execution inBlock = d.statementAnalysis().flowData.guaranteedToBeReachedInCurrentBlock.get();
            FlowData.Execution inMethod = d.statementAnalysis().flowData.guaranteedToBeReachedInMethod.get();
            Map<InterruptsFlow, FlowData.Execution> interruptsFlow = d.statementAnalysis().flowData.interruptsFlow.getOrElse(null);

            if ("method1".equals(d.methodInfo().name)) {

                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("a&&b", d.condition().toString());
                    Assert.assertEquals("a&&b", d.state().toString());
                    Assert.assertEquals(FlowData.Execution.ALWAYS, inBlock);
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                    Assert.assertEquals(Map.of(InterruptsFlow.RETURN, FlowData.Execution.ALWAYS), interruptsFlow);
                }
                if ("0".equals(d.statementId())) {
                    Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, d.condition());
                    Assert.assertEquals("!a||!b", d.state().toString());
                    Assert.assertEquals(FlowData.Execution.ALWAYS, inBlock);
                    Assert.assertEquals(FlowData.Execution.ALWAYS, inMethod);
                    Assert.assertEquals(Map.of(InterruptsFlow.RETURN, FlowData.Execution.CONDITIONALLY), interruptsFlow);
                    Assert.assertEquals(EmptyExpression.EMPTY_EXPRESSION.toString(), d.statementAnalysis().methodLevelData.combinedPrecondition.get().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("!a&&!b", d.condition().toString());
                    Assert.assertEquals("!a&&!b", d.state().toString());
                    Assert.assertEquals(FlowData.Execution.ALWAYS, inBlock);
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("(a||b)&&(!a||!b)", d.state().toString());
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inBlock);
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                    Assert.assertEquals(EmptyExpression.EMPTY_EXPRESSION.toString(), d.statementAnalysis().methodLevelData.combinedPrecondition.get().toString());
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertEquals("a&&!b", d.statementAnalysis().stateData.valueOfExpression.get().toString());
                    Assert.assertEquals("!a&&b", d.state().toString());
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
                    Assert.assertEquals(EmptyExpression.EMPTY_EXPRESSION.toString(), d.statementAnalysis().methodLevelData.combinedPrecondition.get().toString());
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
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (RETURN1.equals(d.variableName())) {
                // return 1;
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("1", d.currentValue().toString());
                    Assert.assertEquals("(" + A1 + " and " + B1 + ")", d.variableInfo().getStateOnAssignment().toString());
                }
                // after if(a&&b) return 1
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("(" + A1 + " and " + B1 + ")?1:<return value>", d.currentValue().toString());
                    Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, d.variableInfo().getStateOnAssignment());
                }
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("2", d.currentValue().toString());
                    Assert.assertEquals("(not (" + A1 + ") and not (" + B1 + "))", d.variableInfo().getStateOnAssignment().toString());
                }
                // after if (!a && !b) return 2;
                if ("1".equals(d.statementId())) {
                    Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, d.variableInfo().getStateOnAssignment());
                    // we do NOT expect a regression to the ReturnVariable
                    Assert.assertEquals("(not (" + A1 + ") and not (" + B1 + "))?2:(" + A1 + " and " + B1 + ")?1:<return value>",
                            d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, d.variableInfo().getStateOnAssignment());
                    // we do NOT expect a regression to the ReturnVariable
                    Assert.assertEquals("(" + A1 + " and not (" + B1 + "))?3:" +
                                    "(not (" + A1 + ") and not (" + B1 + "))?2:(" + A1 + " and " + B1 + ")?1:<return value>",
                            d.currentValue().toString());
                }
                if ("3".equals(d.statementId())) {
                    Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, d.variableInfo().getStateOnAssignment());
                    // we do NOT expect a regression to the ReturnVariable
                    Assert.assertEquals(RETURN_1_VALUE, d.currentValue().toString());
                }
                if ("4".equals(d.statementId())) {
                    Assert.fail("not reached!");
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, d.methodAnalysis().getPrecondition());
                Assert.assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
                Assert.assertEquals(RETURN_1_VALUE, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("ConditionalChecks_0", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    @Test
    public void test1() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method2".equals(d.methodInfo().name) && "0.0.0".equals(d.statementId())) {
                Assert.assertEquals("null==a||null==b", d.state().toString());
                Assert.assertEquals("null==a||null==b", d.condition().toString());
            }
            if ("method2".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                Assert.assertEquals("null!=a&&null!=b", d.state().toString());
                Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, d.condition());
            }
        };
        testClass("ConditionalChecks_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    @Test
    public void test2() throws IOException {
        final String RETURN3 = "org.e2immu.analyser.testexample.ConditionalChecks.method3(String,String)";
        final String A3 = RETURN3 + ":0:a";
        final String B3 = "org.e2immu.analyser.testexample.ConditionalChecks.method3(String,String):1:b";

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method3".equals(d.methodInfo().name)) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.parameterAnalyses().get(0).getProperty(VariableProperty.NOT_NULL));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.parameterAnalyses().get(1).getProperty(VariableProperty.NOT_NULL));
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method3".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, d.condition());
                        Assert.assertEquals("null!=a", d.state().toString());
                        Assert.assertTrue(d.statementAnalysis().stateData.statementContributesToPrecondition.isSet());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        Assert.assertEquals("null==a", d.condition().toString());
                        Assert.assertEquals("null==a", d.state().toString());
                        Assert.assertTrue(d.haveSetProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL));
                        Assert.assertFalse(d.statementAnalysis().stateData.statementContributesToPrecondition.isSet());
                    }
                    if ("1".equals(d.statementId())) {
                        Assert.assertSame(EmptyExpression.EMPTY_EXPRESSION, d.condition());
                        Assert.assertEquals("(not (null == " + A3 + ") " + "and not (null == " + B3 + "))", d.state().toString());
                        Assert.assertTrue(d.statementAnalysis().stateData.statementContributesToPrecondition.isSet());
                    }
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
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

        testClass("ConditionalChecks_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }


    @Test
    public void test_3() throws IOException {
        testClass("ConditionalChecks_3", 0, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }


    @Test
    public void test_4() throws IOException {
        final String RETURN5 = "org.e2immu.analyser.testexample.ConditionalChecks.method5(Object)";
        final String O5 = RETURN5 + ":0:o";
        final String THIS_GET_CLASS = "org.e2immu.analyser.testexample.ConditionalChecks.this.getClass()";
        final String THIS = "org.e2immu.analyser.testexample.ConditionalChecks.this";
        final String O5_GET_CLASS = "org.e2immu.analyser.testexample.ConditionalChecks.method5(Object):0:o.getClass()";
        final String I = "org.e2immu.analyser.testexample.ConditionalChecks.i";
        final String CC_I = "org.e2immu.analyser.testexample.ConditionalChecks.i#" + O5;
        final String RETURN_5_VALUE = I + " == " + CC_I;

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method5".equals(d.methodInfo().name)) {
                if (O5.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertFalse(d.hasProperty(VariableProperty.NOT_NULL));
                    }
                }
                if (CONDITIONAL_CHECKS.equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        Assert.assertEquals(O5, d.currentValue().toString());
                    }
                }
                if ("3".equals(d.statementId())) {
                    if (CC_I.equals(d.variableName())) {
                        String expectValue = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : CC_I; // that's the variable value
                        Assert.assertEquals(expectValue, d.currentValue().toString());
                    }
                    if (RETURN5.equals(d.variableName())) {
                        String expectValue = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : RETURN_5_VALUE;
                        Assert.assertEquals(expectValue, d.currentValue().toString());
                        Assert.assertEquals(VariableInfoContainer.LEVEL_3_EVALUATION, d.variableInfoContainer().getCurrentLevel());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method5".equals(d.methodInfo().name)) {
                // the escape mechanism does NOT kick in!
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("o!=this", d.state().toString());
                } else if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("o==this", d.state().toString());
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
            if ("method5".equals(d.methodInfo().name)) {
                int expect = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                Assert.assertEquals(expect, d.parameterAnalyses().get(0).getProperty(VariableProperty.NOT_NULL));
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method5".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                    Assert.assertEquals("o==this", d.evaluationResult().value.toString());
                }
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                    Assert.assertEquals("true", d.evaluationResult().value.toString());
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                    Assert.assertEquals("null==o||o.getClass()!=this.getClass()", d.evaluationResult().value.toString());
                    Assert.assertTrue(d.evaluationResult().getModificationStream().count() > 0);
                    Assert.assertTrue(d.haveMarkRead(O5));
                    Assert.assertFalse(d.haveSetProperty(O5, VariableProperty.NOT_NULL));
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertFalse(d.haveSetProperty(O5, VariableProperty.NOT_NULL));
                    Assert.assertTrue(d.haveValueChange(CONDITIONAL_CHECKS));
                    Assert.assertEquals(O5, d.findValueChange(CONDITIONAL_CHECKS).value().toString());
                    Assert.assertEquals(O5, d.evaluationResult().value.toString());
                }
                if ("3".equals(d.statementId())) {
                    // there will be two iterations, in the second one, i will not have value "NO_VALUE" anymore
                    String expectValueString = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : I + " == " + I + "#" + O5;
                    Assert.assertEquals(expectValueString, d.evaluationResult().value.toString());
                    if (d.iteration() == 0) {
                        // markRead is only done in the first iteration
                        Assert.assertTrue(d.haveMarkRead(CONDITIONAL_CHECKS));
                        Assert.assertTrue(d.haveMarkRead(I));
                        Assert.assertTrue(d.haveMarkRead(I + "#" + O5));
                    }
                    Assert.assertFalse(d.haveSetProperty(O5, VariableProperty.NOT_NULL));
                    Assert.assertFalse(d.haveSetProperty(CONDITIONAL_CHECKS, VariableProperty.NOT_NULL));
                }
            }
        };

        testClass("ConditionalChecks_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }
}
