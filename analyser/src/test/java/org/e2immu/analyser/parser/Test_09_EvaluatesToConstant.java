package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

public class Test_09_EvaluatesToConstant extends CommonTestRunner {
    private static final String PARAM_2_TO_LOWER = "org.e2immu.analyser.testexample.EvaluatesToConstant.method2(String):0:param.toLowerCase()";
    private static final String PARAM_3_TO_LOWER = "org.e2immu.analyser.testexample.EvaluatesToConstant.method3(String):0:param.toLowerCase()";
    public static final String PARAM_3_CONTAINS = "org.e2immu.analyser.testexample.EvaluatesToConstant.method3(String):0:param.contains(a)";
    public static final String XZY_TO_LOWER_CASE = "xzy.toLowerCase()";
    public static final String PARAM_SOME_TO_LOWER_CASE = "org.e2immu.analyser.testexample.EvaluatesToConstant.someMethod(String):0:a.toLowerCase()";

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
                Assert.assertEquals(PARAM_3_CONTAINS, d.state().toString());

                if (d.iteration() >= 1) {
                    Value value = d.statementAnalysis().stateData.valueOfExpression.get();
                    Assert.assertEquals(XZY_TO_LOWER_CASE, value.toString());
                    Assert.assertTrue("Is " + value.getClass(), value instanceof MethodValue);
                }
            }
            if ("1.0.1".equals(d.statementAnalysis().index)) {
                Assert.assertEquals(PARAM_3_CONTAINS, d.state().toString());
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
            Assert.assertEquals(PARAM_SOME_TO_LOWER_CASE, tv.getValue().toString());
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, tv.getProperty(VariableProperty.NOT_NULL));
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

        if ("method2".equals(d.methodInfo().name) && "b".equals(d.variableName()) && "0".equals(d.statementId())) {
            Assert.assertEquals(PARAM_2_TO_LOWER, d.currentValue().toString());
            int notNull = d.currentValue().getProperty(d.evaluationContext(), VariableProperty.NOT_NULL);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
        }
        if ("method3".equals(d.methodInfo().name)) {
            if ("b".equals(d.variableName()) && d.iteration() >= 1 && Set.of("0", "1").contains(d.statementId())) {
                // this is regardless the statement id, as b is defined in the very first statement
                Assert.assertEquals(d.toString(), PARAM_3_TO_LOWER, d.currentValue().toString());
            }
            if ("a".equals(d.variableName()) && "1.0.0".equals(d.statementId()) && d.iteration() >= 1) {
                Assert.assertEquals(XZY_TO_LOWER_CASE, d.currentValue().toString());
            }

        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("method2".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_1, d.step());
                Assert.assertEquals(PARAM_2_TO_LOWER, d.evaluationResult().value.toString());
            }
            if ("1".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                Assert.assertEquals("false", d.evaluationResult().value.toString());
            }
        }
        if ("method3".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                Assert.assertEquals(PARAM_3_CONTAINS, d.evaluationResult().value.toString());
            }
            if ("1.0.0".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_1, d.step());
                Assert.assertEquals(XZY_TO_LOWER_CASE, d.evaluationResult().value.toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("someMethod".equals(d.methodInfo().name)) {
            Assert.assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
            Assert.assertEquals("inline someMethod on " + PARAM_SOME_TO_LOWER_CASE,
                    d.methodAnalysis().getSingleReturnValue().toString());

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
