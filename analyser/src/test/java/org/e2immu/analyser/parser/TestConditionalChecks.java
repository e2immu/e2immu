package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.FlowData;
import org.e2immu.analyser.analyser.InterruptsFlow;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class TestConditionalChecks extends CommonTestRunner {
    private static final String A1 = "org.e2immu.analyser.testexample.ConditionalChecks.method1(boolean,boolean):0:a";
    private static final String B1 = "org.e2immu.analyser.testexample.ConditionalChecks.method1(boolean,boolean):1:b";
    private static final String A3 = "org.e2immu.analyser.testexample.ConditionalChecks.method3(String,String):0:a";
    private static final String B3 = "org.e2immu.analyser.testexample.ConditionalChecks.method3(String,String):1:b";
    private static final String O5 = "org.e2immu.analyser.testexample.ConditionalChecks.method5(Object):0:o";
    private static final String THIS_GET_CLASS = "org.e2immu.analyser.testexample.ConditionalChecks.this.getClass()";
    private static final String THIS = "org.e2immu.analyser.testexample.ConditionalChecks.this";
    private static final String O5_GET_CLASS = "org.e2immu.analyser.testexample.ConditionalChecks.method5(Object):0:o.getClass()";

    public TestConditionalChecks() {
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
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        FlowData.Execution inBlock = d.statementAnalysis().flowData.guaranteedToBeReachedInCurrentBlock.get();
        FlowData.Execution inMethod = d.statementAnalysis().flowData.guaranteedToBeReachedInMethod.get();
        Map<InterruptsFlow, FlowData.Execution> interruptsFlow = d.statementAnalysis().flowData.interruptsFlow.get();

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
            }
            if ("2".equals(d.statementId())) {
                Assert.assertEquals("(not (" + A1 + ") and " + B1 + ")", d.state().toString());
                Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inBlock);
                Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
            }
            // constant condition
            if ("3".equals(d.statementId())) {
                Assert.assertEquals(d.evaluationContext().boolValueFalse(), d.state());
                Assert.assertEquals("ERROR in M:method1:3: Condition in 'if' or 'switch' statement evaluates to constant",
                        d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
                Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inBlock);
                Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
            }
            // unreachable statement
            if ("4".equals(d.statementId())) {
                Assert.assertEquals(FlowData.Execution.NEVER, inBlock);
                Assert.assertEquals(FlowData.Execution.NEVER, inMethod);
                Assert.assertNotNull(d.haveError(Message.UNREACHABLE_STATEMENT));
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
            }
            if ("1".equals(d.statementId())) {
                Assert.assertEquals("(not (null == " + O5 + ") and " + O5_GET_CLASS + " == " + THIS_GET_CLASS + " and not (" + O5 + " == " + THIS + "))", d.state().toString());
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
        }
        if ("method5".equals(d.methodInfo().name)) {
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.parameterAnalyses().get(0).getProperty(VariableProperty.NOT_NULL));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("ConditionalChecks", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

}
