package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.variable.ReturnVariable;
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
                Assert.assertEquals("param.contains(\"a\")", d.absoluteState().toString());

                if (d.iteration() >= 1) {
                    Expression value = d.statementAnalysis().stateData.getValueOfExpression();
                    Assert.assertEquals("\"xzy\"", value.toString());
                    Assert.assertTrue("Is " + value.getClass(), value instanceof StringConstant);
                }
            }
            if ("1.0.1".equals(d.statementAnalysis().index)) {
                String expectState = d.iteration() == 0 ? "param.contains(\"a\")&&null!=<s:String>" : "param.contains(\"a\")";
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
            Assert.assertEquals("null==a?\"x\":a", variableInfo.getValue().toString());
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                    variableInfo.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

        if ("method2".equals(d.methodInfo().name)) {
            if ("b".equals(d.variableName()) && "0".equals(d.statementId())) {
                Assert.assertEquals("null==param?\"x\":param", d.currentValue().toString());
                int nne = d.currentValue().getProperty(d.evaluationContext(), VariableProperty.NOT_NULL_EXPRESSION, true);
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, nne);
                Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
            }
            if (d.variable() instanceof ParameterInfo p && "param".equals(p.name)) {
                int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                Assert.assertEquals("Statement " + d.statementId() + ", it " + d.iteration(),
                        expectCnn, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
            }
        }

        if ("method3".equals(d.methodInfo().name)) {
            if (d.variable() instanceof ParameterInfo p && "param".equals(p.name)) {
                Assert.assertEquals("nullable instance type String", d.currentValue().toString());
                if ("0".equals(d.statementId())) {
                    int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                    Assert.assertEquals(expectCnn, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
            }
            if ("b".equals(d.variableName()) && "0".equals(d.statementId())) {
                Assert.assertEquals(d.toString(), "null==param?\"x\":param", d.currentValue().toString());
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
            if ("a".equals(d.variableName()) && "1.0.0".equals(d.statementId())) {
                // the delay comes from the CNN == -1 value of PARAM, delayed condition in 1
                String expectValue = d.iteration() == 0 ? "<s:String>" : "\"xzy\"";
                Assert.assertEquals(expectValue, d.currentValue().toString());
                int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                Assert.assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
            if (d.variable() instanceof ReturnVariable) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    Assert.assertEquals(MultiLevel.NOT_INVOLVED, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                }
                if("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals(MultiLevel.NOT_INVOLVED, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                }
                if ("1.0.1.0.0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                        Assert.assertEquals(Level.DELAY, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    } else {
                        Assert.fail(); // unreachable, now that the condition is stable
                    }
                }
                if ("1.0.1".equals(d.statementId())) {
                    Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    int expectEnn = d.iteration() == 0 ? Level.DELAY: MultiLevel.NOT_INVOLVED;
                    Assert.assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    int expectEnn = d.iteration() == 0 ? Level.DELAY: MultiLevel.NOT_INVOLVED;
                    Assert.assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                }
            }
        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("method2".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                Assert.assertEquals("null==param?\"x\":param", d.evaluationResult().value().toString());
            }
            if ("1".equals(d.statementId())) {
                Assert.assertEquals("false", d.evaluationResult().value().toString());
            }
        }
        if ("method3".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementId())) {
                Assert.assertEquals("param.contains(\"a\")", d.evaluationResult().value().toString());
                Assert.assertFalse(d.evaluationResult().someValueWasDelayed());
            }
            if ("1.0.0".equals(d.statementId())) {
                Assert.assertEquals("\"xzy\"", d.evaluationResult().value().toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("someMethod".equals(d.methodInfo().name)) {
            Assert.assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            Assert.assertEquals("null==a?\"x\":a", d.methodAnalysis().getSingleReturnValue().toString());
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                    d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));

            ParameterAnalysis param = d.parameterAnalyses().get(0);
            int expectNnp = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            Assert.assertEquals(expectNnp, param.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        }
    };

    /*
    whether b is used or not: 6 or 4 warnings; not too important at the moment
     */
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
