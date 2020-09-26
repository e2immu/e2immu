package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.value.BoolValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestConditionalChecks extends CommonTestRunner {
    public TestConditionalChecks() {
        super(false);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("method5".equals(d.methodInfo.name) && "o".equals(d.variableName)) {
            if ("0".equals(d.statementId)) {
                Assert.assertNull(d.properties.get(VariableProperty.NOT_NULL));
            }
        }
        if ("method5".equals(d.methodInfo.name) && "conditionalChecks".equals(d.variableName)) {
            if ("2".equals(d.statementId)) {
                Assert.assertEquals("o", d.currentValue.toString());
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method1".equals(d.methodInfo.name)) {
            if ("0.0.0".equals(d.statementId)) {
                Assert.assertEquals("(a and b)", d.condition.toString());
                Assert.assertEquals("(a and b)", d.state.toString());
            }
            if ("0".equals(d.statementId)) {
                Assert.assertSame(UnknownValue.EMPTY, d.condition);
                Assert.assertEquals("(not (a) or not (b))", d.state.toString());
            }
            if ("1.0.0".equals(d.statementId)) {
                Assert.assertEquals("(not (a) and not (b))", d.condition.toString());
                Assert.assertEquals("(not (a) and not (b))", d.state.toString());
            }
            if ("1".equals(d.statementId)) {
                Assert.assertEquals("((a or b) and (not (a) or not (b)))", d.state.toString());
            }
            if ("2".equals(d.statementId)) {
                Assert.assertEquals("(not (a) and b)", d.state.toString());
            }
            // constant condition
            if ("3".equals(d.statementId)) {
                Assert.assertEquals(BoolValue.FALSE, d.state);
                Assert.assertTrue(d.statementAnalysis.errorValue.isSet());
            }
            // unreachable statement
            if ("4".equals(d.statementId)) {
                Assert.assertTrue(d.statementAnalysis.errorValue.isSet());
            }
        }
        if ("method3".equals(d.methodInfo.name)) {
            if (d.iteration == 0) {
                if ("0".equals(d.statementId)) {
                    Assert.assertSame(UnknownValue.EMPTY, d.condition);
                }
                if ("1".equals(d.statementId)) {
                    Assert.assertSame(UnknownValue.EMPTY, d.condition);
                }
            }
        }
        if ("method5".equals(d.methodInfo.name)) {
            // the escape mechanism does NOT kick in!
            if ("0".equals(d.statementId)) {
                Assert.assertEquals("not (this == o)", d.state.toString());
            }
            if ("1".equals(d.statementId)) {
                Assert.assertEquals("(not (null == o) and this.getClass() == o.getClass() and not (this == o))", d.state.toString());
            }
            ParameterInfo o = d.methodInfo.methodInspection.get().parameters.get(0);
            Assert.assertEquals("Numbered statement: " + d.statementId + " iteration " + d.iteration,
                    Level.DELAY, o.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if (iteration == 0 && "method3".equals(methodInfo.name)) {
            ParameterInfo a = methodInfo.methodInspection.get().parameters.get(0);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, a.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
            ParameterInfo b = methodInfo.methodInspection.get().parameters.get(1);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, b.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
        }

        if ("method1".equals(methodInfo.name)) {
            Assert.assertTrue(methodInfo.methodAnalysis.get().precondition.isSet());
            Assert.assertSame(UnknownValue.EMPTY, methodInfo.methodAnalysis.get().precondition.get());
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
