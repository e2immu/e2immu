package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MethodCall;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

public class Test_09_EvaluatesToConstant extends CommonTestRunner {

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
                Assert.assertEquals("param.contains(\"a\")", d.state().toString());

                if (d.iteration() >= 1) {
                    Expression value = d.statementAnalysis().stateData.valueOfExpression.get();
                    Assert.assertEquals("xzy", value.toString());
                    Assert.assertTrue("Is " + value.getClass(), value instanceof MethodCall);
                }
            }
            if ("1.0.1".equals(d.statementAnalysis().index)) {
                Assert.assertEquals("param.contains(\"a\")", d.state().toString());
                if (d.iteration() >= 1) {
                    Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
                }
            }
            if ("1.0.1.0.0".equals(d.statementAnalysis().index)) {
                Assert.assertNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
            }
        }

        if ("someMethod".equals(d.methodInfo().name)) {
            VariableInfo variableInfo = d.getReturnAsVariable();
            Assert.assertEquals("null==a?\"x\":a", variableInfo.getValue().toString());
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, variableInfo.getProperty(VariableProperty.NOT_NULL));
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

        if ("method2".equals(d.methodInfo().name) && "b".equals(d.variableName()) && "0".equals(d.statementId())) {
            Assert.assertEquals("null==param?\"x\":param", d.currentValue().toString());
            int notNull = d.currentValue().getProperty(d.evaluationContext(), VariableProperty.NOT_NULL);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
        }
        if ("method3".equals(d.methodInfo().name)) {
            if ("b".equals(d.variableName()) && Set.of("0", "1").contains(d.statementId())) {
                // this is regardless the statement id, as b is defined in the very first statement
                Assert.assertEquals(d.toString(), "null==param?\"x\":param", d.currentValue().toString());
            }
            if ("a".equals(d.variableName()) && "1.0.0".equals(d.statementId())) {
                Assert.assertEquals("\"xzy\"", d.currentValue().toString());
            }

        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("method2".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_1, d.step());
                Assert.assertEquals("null==param?\"x\":param", d.evaluationResult().value.toString());
            }
            if ("1".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                Assert.assertEquals("false", d.evaluationResult().value.toString());
            }
        }
        if ("method3".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                Assert.assertEquals("param.contains(\"a\")", d.evaluationResult().value.toString());
            }
            if ("1.0.0".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_1, d.step());
                Assert.assertEquals("\"xzy\"", d.evaluationResult().value.toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("someMethod".equals(d.methodInfo().name)) {
            Assert.assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
            Assert.assertEquals("null==a?\"x\":a", d.methodAnalysis().getSingleReturnValue().toString());

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
