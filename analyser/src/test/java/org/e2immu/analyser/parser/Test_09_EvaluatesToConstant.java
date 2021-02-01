package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.StringConstant;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

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
                String expectState = d.iteration() == 0 ? "<method:java.lang.String.contains(CharSequence)>" : "param.contains(\"a\")";
                Assert.assertEquals(expectState, d.absoluteState().toString());

                if (d.iteration() >= 1) {
                    Expression value = d.statementAnalysis().stateData.getValueOfExpression();
                    Assert.assertEquals("\"xzy\"", value.toString());
                    Assert.assertTrue("Is " + value.getClass(), value instanceof StringConstant);
                }
            }
            if ("1.0.1".equals(d.statementAnalysis().index)) {
                String expectState = d.iteration() == 0 ?
                        "<method:java.lang.String.contains(CharSequence)>&&null!=<method:org.e2immu.analyser.testexample.EvaluatesToConstant.someMethod(String)>"
                        :"param.contains(\"a\")";
                Assert.assertEquals(expectState, d.absoluteState().toString());
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
            String expectValue = d.iteration() == 0 ?
                    "null==<parameter:org.e2immu.analyser.testexample.EvaluatesToConstant.someMethod(String):0:a>?\"x\":a" : "null==a?\"x\":a";
            Assert.assertEquals(expectValue, variableInfo.getValue().toString());
            int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
            Assert.assertEquals(expectNN, variableInfo.getProperty(VariableProperty.NOT_NULL));
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

        if ("method2".equals(d.methodInfo().name) && "b".equals(d.variableName()) && "0".equals(d.statementId())) {
            String expectValue = d.iteration() == 0 ? "<method:org.e2immu.analyser.testexample.EvaluatesToConstant.someMethod(String)>" :
                    "null==param?\"x\":param";
            Assert.assertEquals(expectValue, d.currentValue().toString());
            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
            Assert.assertEquals(expectNotNull, d.currentValue().getProperty(d.evaluationContext(), VariableProperty.NOT_NULL));
            String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
            Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
        }

        if ("method3".equals(d.methodInfo().name)) {
            if (d.variable() instanceof ParameterInfo p && "param".equals(p.name)) {
                String expectValue = d.iteration() == 0 ? "<parameter:org.e2immu.analyser.testexample.EvaluatesToConstant.method3(String):0:param>" : "nullable? instance type String";
                Assert.assertEquals(expectValue, d.currentValue().toString());
            }
            if ("b".equals(d.variableName()) && "0".equals(d.statementId())) {
                // this is regardless the statement id, as b is defined in the very first statement
                String expectValue = d.iteration() == 0 ?
                        "<method:org.e2immu.analyser.testexample.EvaluatesToConstant.someMethod(String)>" : "param";
                Assert.assertEquals(d.toString(), expectValue, d.currentValue().toString());
            }
            if ("a".equals(d.variableName()) && "1.0.0".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ?
                        "<method:org.e2immu.analyser.testexample.EvaluatesToConstant.someMethod(String)>" : "\"xzy\"";
                Assert.assertEquals(expectValue, d.currentValue().toString());
            }

        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("method2".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ?
                        "<method:org.e2immu.analyser.testexample.EvaluatesToConstant.someMethod(String)>" : "null==param?\"x\":param";
                Assert.assertEquals(expectValue, d.evaluationResult().value().toString());
            }
            if ("1".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "null==<method:org.e2immu.analyser.testexample.EvaluatesToConstant.someMethod(String)>" : "false";
                Assert.assertEquals(expectValue, d.evaluationResult().value().toString());
            }
        }
        if ("method3".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<method:java.lang.String.contains(CharSequence)>" : "param.contains(\"a\")";
                Assert.assertEquals(expectValue, d.evaluationResult().value().toString());
            }
            if ("1.0.0".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<method:org.e2immu.analyser.testexample.EvaluatesToConstant.someMethod(String)>"
                        : "\"xzy\"";
                Assert.assertEquals(expectValue, d.evaluationResult().value().toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("someMethod".equals(d.methodInfo().name)) {
            int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
            Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));

            if (d.iteration() == 0) {
                Assert.assertNull(d.methodAnalysis().getSingleReturnValue());
            } else {
                Assert.assertEquals("null==a?\"x\":a", d.methodAnalysis().getSingleReturnValue().toString());
            }
            int notNull = d.methodAnalysis().getProperty(VariableProperty.NOT_NULL);
            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
            Assert.assertEquals(expectNotNull, notNull);
        }
    };

    @Test
    public void test() throws IOException {
        testClass("EvaluatesToConstant", 4, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

}
