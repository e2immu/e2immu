package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.BoolValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

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

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("method1".equals(methodInfo.name)) {
                if ("0".equals(numberedStatement.streamIndices())) {
                    Assert.assertEquals("(not (a) or not (b))", conditional.toString());
                }
                if ("1".equals(numberedStatement.streamIndices())) {
                    Assert.assertEquals("((a or b) and (not (a) or not (b)))", conditional.toString());
                }
                if ("2".equals(numberedStatement.streamIndices())) {
                    Assert.assertEquals("(not (a) and b)", conditional.toString());
                }
                if ("3".equals(numberedStatement.streamIndices())) {
                    Assert.assertEquals(BoolValue.FALSE, conditional);
                    Assert.assertTrue(numberedStatement.errorValue.isSet());
                }
                if ("4".equals(numberedStatement.streamIndices())) {
                    Assert.assertTrue(numberedStatement.errorValue.isSet());
                }
            }
            if ("method3".equals(methodInfo.name)) {
                if (iteration == 0) {
                    if ("0".equals(numberedStatement.streamIndices())) {
                        Assert.assertNull(conditional);
                        Assert.assertEquals(1, numberedStatement.removeVariablesFromConditional.size());
                    }
                    if ("1".equals(numberedStatement.streamIndices())) {
                        Assert.assertNull(conditional);
                    }
                }
            }
            if ("method5".equals(methodInfo.name)) {
                // the escape mechanism does NOT kick in!
                Assert.assertTrue(numberedStatement.removeVariablesFromConditional.isEmpty());

                if ("0".equals(numberedStatement.streamIndices())) {
                    Assert.assertEquals("not (o == this)", conditional.toString());
                }
                if ("1".equals(numberedStatement.streamIndices())) {
                    Assert.assertEquals("(not (null == o) and o.getClass() == this.getClass() and not (o == this))", conditional.toString());
                }
                ParameterInfo o = methodInfo.methodInspection.get().parameters.get(0);
                Assert.assertEquals("Numbered statement: " + numberedStatement.streamIndices() + " iteration " + iteration,
                        Level.DELAY, o.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            if (iteration == 0 && "method3".equals(methodInfo.name)) {
                ParameterInfo a = methodInfo.methodInspection.get().parameters.get(0);
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, a.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
                ParameterInfo b = methodInfo.methodInspection.get().parameters.get(1);
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, b.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("ConditionalChecks", 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
