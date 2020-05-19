package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestSimpleNotModifiedChecks extends CommonTestRunner {
    public TestSimpleNotModifiedChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = (iteration, methodInfo, statementId, variableName,
                                                                         variable, currentValue, properties) -> {
        if ("add4".equals(methodInfo.name) && "local4".equals(variableName) && "1".equals(statementId)) {
            if (1 == iteration) {
                Assert.assertNull(properties.get(VariableProperty.NOT_NULL));
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement) {
            if ("add4".equals(methodInfo.name) && "1".equals(numberedStatement.streamIndices())) {
                Assert.assertFalse(numberedStatement.errorValue.isSet()); // no potential null pointer exception
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = new FieldAnalyserVisitor() {
        @Override
        public void visit(int iteration, FieldInfo fieldInfo) {
            if (fieldInfo.name.equals("set4")) {
                if (iteration == 0) {
                    Assert.assertEquals(Level.DELAY, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL));
                }
                if (iteration == 1) {
                    Assert.assertEquals(Level.TRUE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL));
                    Assert.assertEquals(1, fieldInfo.fieldAnalysis.get().variablesLinkedToMe.get().size());
                    Assert.assertEquals("in4", fieldInfo.fieldAnalysis.get().variablesLinkedToMe.get().stream().findFirst().orElseThrow().name());
                    Assert.assertTrue(fieldInfo.fieldAnalysis.get().effectivelyFinalValue.isSet());
                }
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            if ("Example4".equals(methodInfo.name)) {
                ParameterInfo in4 = methodInfo.methodInspection.get().parameters.get(0);
                if (iteration == 2) {
                    Assert.assertEquals(Level.FALSE, in4.parameterAnalysis.get().getProperty(VariableProperty.NOT_MODIFIED));
                }
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("SimpleNotModifiedChecks", 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
