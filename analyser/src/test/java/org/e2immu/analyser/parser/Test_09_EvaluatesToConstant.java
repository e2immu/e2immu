package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

public class Test_09_EvaluatesToConstant extends CommonTestRunner {
    private static final String PARAM_3_TO_LOWER = "org.e2immu.analyser.testexample.EvaluatesToConstant.method3(String):0:param.toLowerCase()";

    public Test_09_EvaluatesToConstant() {
        super(true);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method2".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementAnalysis().index) && d.iteration() >= 1) {
                Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
            }
            if ("1.0.0".equals(d.statementAnalysis().index)) {
                Assert.assertNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
            }
        }

        if ("method3".equals(d.methodInfo().name)) {
            if ("1.0.0".equals(d.statementId())) {
                String expectState = "org.e2immu.analyser.testexample.EvaluatesToConstant.method3(String):0:param.contains(a)";
                Assert.assertEquals(expectState, d.state().toString());

                if (d.iteration() >= 1) {
                    Value value = d.statementAnalysis().stateData.valueOfExpression.get();
                    Assert.assertEquals("xzy.toLowerCase()", value.toString());
                    Assert.assertTrue("Is " + value.getClass(), value instanceof MethodValue);
                }
            }
            if ("1.0.1".equals(d.statementAnalysis().index)) {
                String expectStateString = d.iteration() == 0 ? UnknownValue.NO_VALUE.toString() :
                        "org.e2immu.analyser.testexample.EvaluatesToConstant.method3(String):0:param.contains(a)";
                Assert.assertEquals(expectStateString, d.state().toString());
                if (d.iteration() >= 1) {
                    Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
                }
            }
            if ("1.0.1.0.0".equals(d.statementAnalysis().index)) {
                Assert.assertNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
            }
        }

        if ("someMethod".equals(d.methodInfo().name)) {
            VariableInfo tv = d.getReturnAsVariable();
            Assert.assertEquals("org.e2immu.analyser.testexample.EvaluatesToConstant.someMethod(String):0:a.toLowerCase()",
                    tv.getValue().toString());
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, tv.getProperty(VariableProperty.NOT_NULL));
        }
    };

    /*
     private static void checkArray2() {
        int[] integers = {1, 2, 3};
        int i = 0;
        integers[i] = 3;
        // ERROR: assignment is not used
    }
     */

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

        if ("method2".equals(d.methodInfo().name) && "b".equals(d.variableName()) && "0".equals(d.statementId())) {
            String expectValue = d.iteration() == 0 ? UnknownValue.NO_VALUE.toString() :
                    "org.e2immu.analyser.testexample.EvaluatesToConstant.method2(String):0:param.toLowerCase()";
            Assert.assertEquals(expectValue, d.currentValue().toString());
            int expectNotNull = d.iteration() == 0 ? MultiLevel.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
            int notNull = d.currentValue().getProperty(d.evaluationContext(), VariableProperty.NOT_NULL);
            Assert.assertEquals(expectNotNull, notNull);
        }
        if ("method3".equals(d.methodInfo().name)) {
            if ("b".equals(d.variableName()) && d.iteration() >= 1 && Set.of("0", "1").contains(d.statementId())) {
                // this is regardless the statement id, as b is defined in the very first statement
                Assert.assertEquals(d.toString(), PARAM_3_TO_LOWER, d.currentValue().toString());
            }
            if ("a".equals(d.variableName()) && "1.0.0".equals(d.statementId()) && d.iteration() >= 1) {
                Assert.assertEquals("xzy.toLowerCase()", d.currentValue().toString());
            }
        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("method2".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_2, d.step());
                String expectValueString = d.iteration() == 0 ? UnknownValue.NO_VALUE.toString() :
                        "org.e2immu.analyser.testexample.EvaluatesToConstant.method2(String):0:param.toLowerCase()";
                Assert.assertEquals(expectValueString,
                        d.evaluationResult().value.toString());
            }
            if ("1".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_4, d.step());
                String expectString = d.iteration() == 0 ? UnknownValue.NO_VALUE.toString() : "false";
                Assert.assertEquals(expectString, d.evaluationResult().value.toString());
            }
        }
        if ("method3".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_4, d.step());
                Assert.assertEquals("org.e2immu.analyser.testexample.EvaluatesToConstant.method3(String):0:param.contains(a)",
                        d.evaluationResult().value.toString());
            }
            if ("1.0.0".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_2, d.step());
                String expectValueString = d.iteration() == 0 ? UnknownValue.NO_VALUE.toString() : "xzy.toLowerCase()";
                Assert.assertEquals(expectValueString, d.evaluationResult().value.toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("someMethod".equals(d.methodInfo().name)) {
            Assert.assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
            if (d.iteration() == 0) {
                // single return value is set before modified in the method analyser
                Assert.assertNull(d.methodAnalysis().getSingleReturnValue());
            } else {
                Assert.assertEquals("inline someMethod on org.e2immu.analyser.testexample.EvaluatesToConstant.someMethod(String):0:a.toLowerCase()",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            int notNull = d.methodAnalysis().getProperty(VariableProperty.NOT_NULL);
            int expectNotNull = MultiLevel.EFFECTIVELY_NOT_NULL;
            Assert.assertEquals(expectNotNull, notNull);
        }
    };

    @Test
    public void test() throws IOException {
        testClass("EvaluatesToConstant", 6, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

}