package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class Test_02_ConditionalChecks extends CommonTestRunner {

    public Test_02_ConditionalChecks() {
        super(false);
    }

    @Test
    public void test0() throws IOException {
        final String RETURN1 = "org.e2immu.analyser.testexample.ConditionalChecks_0.method1(boolean,boolean)";
        final String RETURN_1_VALUE = "!a&&b?4:a&&!b?3:!a&&!b?2:a&&b?1:<return value>";

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            FlowData.Execution inBlock = d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock();
            FlowData.Execution inMethod = d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod();
            Map<InterruptsFlow, FlowData.Execution> interruptsFlow = d.statementAnalysis().flowData.getInterruptsFlow();

            if ("method1".equals(d.methodInfo().name)) {

                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("a&&b", d.condition().toString());
                    Assert.assertEquals("true", d.state().toString());
                    Assert.assertEquals("a&&b", d.absoluteState().toString());
                    Assert.assertEquals(FlowData.Execution.ALWAYS, inBlock);
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                    Assert.assertEquals(Map.of(InterruptsFlow.RETURN, FlowData.Execution.ALWAYS), interruptsFlow);
                }
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("true", d.condition().toString());
                    Assert.assertEquals("!a||!b", d.state().toString());
                    Assert.assertEquals(FlowData.Execution.ALWAYS, inBlock);
                    Assert.assertEquals(FlowData.Execution.ALWAYS, inMethod);
                    Assert.assertEquals(Map.of(InterruptsFlow.RETURN, FlowData.Execution.CONDITIONALLY), interruptsFlow);
                    Assert.assertEquals("true", d.statementAnalysis().methodLevelData.getCombinedPrecondition().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("!a&&!b", d.condition().toString());
                    Assert.assertEquals("true", d.state().toString());
                    Assert.assertEquals("!a&&!b", d.absoluteState().toString());
                    Assert.assertEquals(FlowData.Execution.ALWAYS, inBlock);
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("(a||b)&&(!a||!b)", d.state().toString());
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inBlock);
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                    Assert.assertEquals("true", d.statementAnalysis().methodLevelData.getCombinedPrecondition().toString());
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertEquals("a&&!b", d.statementAnalysis().stateData.getValueOfExpression().toString());
                    Assert.assertEquals("!a&&b", d.state().toString());
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inBlock);
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                }
                // constant condition
                if ("3".equals(d.statementId())) {
                    Assert.assertEquals("true", d.statementAnalysis().stateData.getValueOfExpression().toString());
                    Assert.assertEquals("false", d.state().toString()); // after the statement...
                    Assert.assertEquals("ERROR in M:method1:3: Condition in 'if' or 'switch' statement evaluates to constant",
                            d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inBlock);
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                    Assert.assertEquals("true", d.statementAnalysis().methodLevelData.getCombinedPrecondition().toString());
                }
                // unreachable statement
                if ("4".equals(d.statementId())) {
                    Assert.assertEquals(FlowData.Execution.NEVER, inBlock);
                    Assert.assertEquals(FlowData.Execution.NEVER, inMethod);
                    Assert.assertNotNull(d.haveError(Message.UNREACHABLE_STATEMENT));
                    Assert.assertFalse(d.statementAnalysis().methodLevelData.combinedPreconditionIsSet());
                }
                if ("5".equals(d.statementId())) {
                    Assert.assertEquals(FlowData.Execution.NEVER, inBlock);
                    Assert.assertEquals(FlowData.Execution.NEVER, inMethod);
                    Assert.assertNull(d.haveError(Message.UNREACHABLE_STATEMENT));
                    VariableInfo ret = d.getReturnAsVariable();
                    Assert.assertNull(ret); // unreachable statement, no data have even been copied!
                    Assert.assertFalse(d.statementAnalysis().methodLevelData.combinedPreconditionIsSet());
                }

            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (RETURN1.equals(d.variableName())) {
                // return 1;
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("1", d.currentValue().toString());
                }
                // after if(a&&b) return 1
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("a&&b?1:<return value>", d.currentValue().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("2", d.currentValue().toString());
                }
                // after if (!a && !b) return 2;
                if ("1".equals(d.statementId())) {
                    // we do NOT expect a regression to the ReturnVariable
                    Assert.assertEquals("!a&&!b?2:a&&b?1:<return value>",
                            d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    // we do NOT expect a regression to the ReturnVariable
                    Assert.assertEquals("a&&!b?3:!a&&!b?2:a&&b?1:<return value>",
                            d.currentValue().toString());
                }
                if ("3.0.0".equals(d.statementId())) {
                    Assert.assertEquals("4", d.currentValue().toString());
                }
                if ("3".equals(d.statementId())) {
                    // nothing is possible anymore
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
                Assert.assertEquals("true", d.methodAnalysis().getPrecondition().toString());
                Assert.assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                //     Assert.assertEquals(RETURN_1_VALUE, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("ConditionalChecks_0", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    /*
    The escape is purely based on parameters that should not be null. This condition does not go into the precondition,
    it simply forces a @NotNull on the parameter.
     */
    @Test
    public void test1() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method2".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("null==a||null==b", d.absoluteState().toString());
                    Assert.assertEquals("null==a||null==b", d.condition().toString());
                    Assert.assertEquals("true", d.statementAnalysis().stateData.getPrecondition().toString());
                }
                if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                    if (d.iteration() > 0) {
                        Assert.assertEquals("true", d.state().toString());
                        Assert.assertEquals("true", d.condition().toString());
                        Assert.assertEquals("true", d.statementAnalysis().stateData.getPrecondition().toString());
                        Assert.assertEquals("true", d.statementAnalysis().methodLevelData.getCombinedPrecondition().toString());
                    }
                }
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
                for(int param: new int[] { 0, 1}) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                            d.parameterAnalyses().get(param).getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    Assert.assertEquals(MultiLevel.NULLABLE,
                            d.parameterAnalyses().get(param).getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                            d.parameterAnalyses().get(param).getProperty(VariableProperty.NOT_NULL_VARIABLE));
                }

                Assert.assertEquals(0, d.methodAnalysis().getCompanionAnalyses().size());
                Assert.assertEquals(0, d.methodAnalysis().getComputedCompanions().size());
                Assert.assertTrue(d.methodAnalysis().getPrecondition().isBoolValueTrue());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method3".equals(d.methodInfo().name)) {
                if (d.iteration() == 1) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertEquals("true", d.condition().toString());
                        Assert.assertEquals("true", d.state().toString()); //->precondition, in this case, parameter not null
                        // goes into not-null on parameters
                        Assert.assertEquals("true", d.statementAnalysis().methodLevelData.getCombinedPrecondition().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        Assert.assertEquals("null==a", d.condition().toString());
                        Assert.assertEquals("null==a", d.absoluteState().toString());
                        // not-null does not contribute to the precondition
                        Assert.assertEquals("true", d.statementAnalysis().stateData.getPrecondition().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        Assert.assertEquals("true", d.condition().toString());
                        Assert.assertEquals("true", d.state().toString()); // in both parameters by now
                        //      Assert.assertTrue(d.statementAnalysis().stateData.statementContributesToPrecondition.isSet());
                        Assert.assertEquals("true", d.statementAnalysis().methodLevelData.getCombinedPrecondition().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        Assert.assertEquals("null==b", d.condition().toString());
                        Assert.assertEquals("null==b", d.absoluteState().toString()); // null!=a in parameter @NotNull
                        Assert.assertEquals("true", d.statementAnalysis().stateData.getPrecondition().toString());
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
            if (d.variable() instanceof ParameterInfo a && "a".equals(a.name)) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
            }
            if (d.variable() instanceof ParameterInfo b && "b".equals(b.name)) {
                if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
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
    public void test3() throws IOException {
        testClass("ConditionalChecks_3", 0, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }


    @Test
    public void test4() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.ConditionalChecks_4";
        final String RETURN5 = TYPE + ".method5(Object)";
        final String O5 = RETURN5 + ":0:o";
        final String I = TYPE + ".i";
        final String CC_I = TYPE + ".i#" + O5;
        final String CONDITIONAL_CHECKS = "conditionalChecks";
        final String O_I_DELAYED = "<field:org.e2immu.analyser.testexample.ConditionalChecks_4.i#org.e2immu.analyser.testexample.ConditionalChecks_4.method5(Object):0:o>";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method5".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    if (O5.equals(d.variableName())) {
                        int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.MUTABLE;
                        Assert.assertEquals(expectNN, d.getProperty(VariableProperty.NOT_NULL_VARIABLE));
                        Assert.assertEquals(LinkedVariables.EMPTY, d.variableInfo().getStaticallyAssignedVariables());
                        Assert.assertEquals("nullable? instance type Object", d.currentValue().toString());
                        Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if (RETURN5.equals(d.variableName())) {
                        Assert.assertEquals("<return value>||o==this", d.currentValue().toString());
                    }
                }
                if ("2".equals(d.statementId())) {
                    if (CONDITIONAL_CHECKS.equals(d.variableName())) {//d.iteration() == 0 ? O :
                        Assert.assertEquals("o", d.currentValue().toString());
                    }
                }
                if ("3".equals(d.statementId())) {
                    if (CC_I.equals(d.variableName())) {
                        String expectValue = d.iteration() == 0 ? O_I_DELAYED : "instance type int";
                        Assert.assertEquals(expectValue, d.currentValue().toString());
                        Assert.assertEquals(d.iteration() == 0, d.currentValueIsDelayed());
                    }
                    if (RETURN5.equals(d.variableName())) {
                        String expectValue = d.iteration() == 0 ?
                                "null!=<parameter:org.e2immu.analyser.testexample.ConditionalChecks_4.method5(Object):0:o>&&o.getClass()==this.getClass()&&o!=this&&<field:org.e2immu.analyser.testexample.ConditionalChecks_4.i#conditionalChecks>==<field:org.e2immu.analyser.testexample.ConditionalChecks_4.i>" :
                                "null!=o&&o.getClass()==this.getClass()&&i==o.i&&o!=this";
                        Assert.assertEquals(expectValue, d.currentValue().toString());
                        Assert.assertEquals(d.iteration() == 0, d.currentValueIsDelayed());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method5".equals(d.methodInfo().name)) {
                // the escape mechanism does NOT kick in!
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("o!=this", d.absoluteState().toString());
                } else if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("o==this", d.absoluteState().toString());
                } else if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("o!=this&&(null==o||o.getClass()!=this.getClass())", d.absoluteState().toString());
                } else {
                    Assert.assertEquals("null!=o&&o.getClass()==this.getClass()&&o!=this", d.absoluteState().toString());
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertEquals("null!=o&&o.getClass()==this.getClass()&&o!=this", d.absoluteState().toString());
                }
                if ("3".equals(d.statementId())) {
                    AnalysisStatus expectStatus = d.iteration() == 0 ? AnalysisStatus.PROGRESS : AnalysisStatus.DONE;
                    Assert.assertEquals(d.toString(), expectStatus, d.result().analysisStatus);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method5".equals(d.methodInfo().name)) {
                int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                Assert.assertEquals(expectNN, d.parameterAnalyses().get(0).getProperty(VariableProperty.NOT_NULL_VARIABLE));
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method5".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("o==this", d.evaluationResult().value().toString());
                    Assert.assertFalse(d.haveSetProperty(O5, VariableProperty.CONTEXT_NOT_NULL));
                }
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("true", d.evaluationResult().value().toString());
                    Assert.assertFalse(d.haveSetProperty(O5, VariableProperty.CONTEXT_NOT_NULL));
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertFalse(d.haveSetProperty(O5, VariableProperty.CONTEXT_NOT_NULL));
                    Assert.assertEquals("null==o||o.getClass()!=this.getClass()", d.evaluationResult().value().toString());
                    Assert.assertFalse(d.haveMarkRead(O5));
                    Variable o5 = d.evaluationResult().changeData().keySet().stream().filter(v -> v.simpleName().equals("o")).findFirst().orElseThrow();
                    Assert.assertEquals(LinkedVariables.EMPTY, d.evaluationResult().changeData().get(o5).staticallyAssignedVariables());
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertFalse(d.haveSetProperty(O5, VariableProperty.CONTEXT_NOT_NULL));
                    Assert.assertTrue(d.haveValueChange(CONDITIONAL_CHECKS));
                    Assert.assertEquals("o", d.findValueChange(CONDITIONAL_CHECKS).value().toString());
                    Assert.assertEquals("o", d.evaluationResult().value().toString());
                }
                if ("3".equals(d.statementId())) {
                    String expectValueString = d.iteration() == 0 ? "<field:org.e2immu.analyser.testexample.ConditionalChecks_4.i#org.e2immu.analyser.testexample.ConditionalChecks_4.method5(java.lang.Object):0:o>==<field:org.e2immu.analyser.testexample.ConditionalChecks_4.i>"
                            : "i==o.i";
                    Assert.assertEquals(expectValueString, d.evaluationResult().value().toString());
                    Assert.assertEquals(d.iteration() == 0, d.evaluationResult().someValueWasDelayed());

                    if (d.iteration() == 0) {
                        // markRead is only done in the first iteration
                        Assert.assertTrue(d.haveMarkRead(CONDITIONAL_CHECKS));
                        Assert.assertTrue(d.haveMarkRead(I));
                        Assert.assertFalse(d.haveMarkRead(I + "#" + O5));
                    }
                    Assert.assertFalse(d.haveSetProperty(O5, VariableProperty.CONTEXT_NOT_NULL));
                    Assert.assertFalse(d.haveSetProperty(CONDITIONAL_CHECKS, VariableProperty.CONTEXT_NOT_NULL));
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

    @Test
    public void test5x() {
        Assert.assertEquals("abc", method(0, 10));
        Assert.assertEquals("xyz", method(0, 0));
        Assert.assertEquals("tuv", method(10, -10));
        Assert.assertNull(method(10, 10));
    }

    private static String method(int p, int q) {
        return p <= 2 ? q >= 5 ? "abc" : "xyz" : q <= -1 ? "tuv" : null;
    }

    @Test
    public void test5() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                Assert.assertEquals("q<=-1||p<=2", d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name) & "s".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("q>=5?\"abc\":\"xyz\"", d.currentValue().toString());
                }
                if ("1.1.0".equals(d.statementId())) {
                    Assert.assertEquals("q<=-1?\"tuv\":null", d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("p<=2?q>=5?\"abc\":\"xyz\":q<=-1?\"tuv\":null", d.currentValue().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("q>=5", d.condition().toString());
                    Assert.assertEquals("q>=5&&p<=2", d.absoluteState().toString());
                }
                if ("1.0.0.1.0".equals(d.statementId())) {
                    Assert.assertEquals("q<=4", d.condition().toString());
                    Assert.assertEquals("p<=2&&q<=4", d.absoluteState().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("p<=2", d.condition().toString());
                    Assert.assertEquals("p<=2", d.absoluteState().toString());
                }
                if ("1.1.0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("q<=-1", d.condition().toString());
                    Assert.assertEquals("p>=3&&q<=-1", d.absoluteState().toString());
                }
                if ("1.1.0".equals(d.statementId())) {
                    Assert.assertEquals("p>=3", d.condition().toString());
                    Assert.assertEquals("p>=3", d.absoluteState().toString());
                }
            }
        };
        testClass("ConditionalChecks_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    @Test
    public void test6() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                Assert.assertEquals("true", d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name) & "s".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("q>=5?\"abc\":\"xyz\"", d.currentValue().toString());
                }
                if ("1.1.0".equals(d.statementId())) {
                    Assert.assertEquals("q<=-1?\"tuv\":\"zzz\"", d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("p<=2?q>=5?\"abc\":\"xyz\":q<=-1?\"tuv\":\"zzz\"", d.currentValue().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("q>=5", d.condition().toString());
                    Assert.assertEquals("q>=5&&p<=2", d.absoluteState().toString());
                }
                if ("1.0.0.1.0".equals(d.statementId())) {
                    Assert.assertEquals("q<=4", d.condition().toString());
                    Assert.assertEquals("p<=2&&q<=4", d.absoluteState().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("p<=2", d.condition().toString());
                    Assert.assertEquals("p<=2", d.absoluteState().toString());
                }
                if ("1.1.0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("q<=-1", d.condition().toString());
                    Assert.assertEquals("p>=3&&q<=-1", d.absoluteState().toString());
                }
                if ("1.1.0.1.0".equals(d.statementId())) {
                    Assert.assertEquals("q>=0", d.condition().toString());
                    Assert.assertEquals("p>=3&&q>=0", d.absoluteState().toString());
                }
                if ("1.1.0".equals(d.statementId())) {
                    Assert.assertEquals("p>=3", d.condition().toString());
                    Assert.assertEquals("p>=3", d.absoluteState().toString());
                }
            }
        };
        testClass("ConditionalChecks_6", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    @Test
    public void test7() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("3".equals(d.statementId())) {
                    Assert.assertEquals("(q>=5||p>=3)&&(q>=5||q<=-1)&&(p>=3||p<=2)&&(q<=-1||p<=2)",
                            d.evaluationResult().value().toString());
                }
                if ("4".equals(d.statementId())) {
                    Assert.assertEquals("(q>=5||p>=3)&&(q>=5||q<=-1)&&(p>=3||p<=2)&&(q<=-1||p<=2)",
                            d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2.0.0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("q>=5", d.condition().toString());
                    Assert.assertEquals("q>=5&&p<=2", d.absoluteState().toString());
                }
                if ("2.0.0.1.0".equals(d.statementId())) {
                    Assert.assertEquals("q<=4", d.condition().toString());
                    Assert.assertEquals("p<=2&&q<=4", d.absoluteState().toString());
                }
                if ("2.0.0".equals(d.statementId())) {
                    Assert.assertEquals("p<=2", d.condition().toString());
                    Assert.assertEquals("p<=2", d.absoluteState().toString());
                }
                if ("2.1.0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("q<=-1", d.condition().toString());
                    Assert.assertEquals("p>=3&&q<=-1", d.absoluteState().toString());
                }
                if ("2.1.0.1.0".equals(d.statementId())) {
                    Assert.assertEquals("q>=0", d.condition().toString());
                    Assert.assertEquals("p>=3&&q>=0", d.absoluteState().toString());
                }
                if ("2.1.0".equals(d.statementId())) {
                    Assert.assertEquals("p>=3", d.condition().toString());
                    Assert.assertEquals("p>=3", d.absoluteState().toString());
                }
                if ("4".equals(d.statementId())) {
                    Assert.assertEquals("(q>=5||p>=3)&&(q>=5||q<=-1)&&(p>=3||p<=2)&&(q<=-1||p<=2)",
                            d.localConditionManager().precondition().toString());
                    Assert.assertEquals("true", d.state().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name) & "s".equals(d.variableName())) {
                if ("2.0.0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("\"abc\"", d.currentValue().toString());
                }
                if ("2.0.0".equals(d.statementId())) {
                    Assert.assertEquals("q>=5?\"abc\":null", d.currentValue().toString());
                }
                if ("2.1.0".equals(d.statementId())) {
                    Assert.assertEquals("q<=-1?\"tuv\":null", d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertEquals("p<=2?q>=5?\"abc\":null:q<=-1?\"tuv\":null", d.currentValue().toString());
                }
            }
            if ("method".equals(d.methodInfo().name) & "t".equals(d.variableName())) {
                if ("2".equals(d.statementId())) {
                    Assert.assertEquals("p<=2?q>=5?null:\"xyz\":q<=-1?null:\"zzz\"", d.currentValue().toString());
                }
            }
        };

        testClass("ConditionalChecks_7", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }
}
