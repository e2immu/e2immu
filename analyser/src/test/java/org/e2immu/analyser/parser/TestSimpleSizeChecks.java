package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.FinalFieldValueObjectFlowInContext;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestSimpleSizeChecks extends CommonTestRunner {
    public TestSimpleSizeChecks() {
        super(true);
    }

    private static final int SIZE_EQUALS_2 = Analysis.encodeSizeEquals(2);

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("method2".equals(d.methodInfo.name) && "0".equals(d.statementId) && "SimpleSizeChecks.this.intSet".equals(d.variableName)) {
            if (d.iteration > 0) {
                Assert.assertEquals("intSet", d.currentValue.toString());
                Assert.assertTrue(d.currentValue instanceof FinalFieldValueObjectFlowInContext);

                if (d.iteration > 1) {
                    //   Assert.assertEquals(SIZE_EQUALS_2, currentValue.getPropertyOutsideContext(VariableProperty.SIZE));
                }
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("method1".equals(methodInfo.name) && "1".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.isSet());
            }
            if ("method1bis".equals(methodInfo.name) && "1".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.isSet());
            }
            if ("method1bis".equals(methodInfo.name) && "2".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.isSet());
            }
        }
    };

    private static final int SIZE_EQUALS_1 = Analysis.encodeSizeEquals(1);

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
            if ("method1".equals(methodInfo.name)) {
                TransferValue tv = methodAnalysis.returnStatementSummaries.get("2");
                Assert.assertNotNull(tv);
                Assert.assertEquals(MultiLevel.compose(Level.TRUE, Level.NOT_NULL_1), tv.properties.get(VariableProperty.NOT_NULL));
                Assert.assertEquals(SIZE_EQUALS_1, tv.properties.get(VariableProperty.SIZE)); // (1)
                Assert.assertEquals(SIZE_EQUALS_1, methodAnalysis.getProperty(VariableProperty.SIZE));
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = new FieldAnalyserVisitor() {
        @Override
        public void visit(int iteration, FieldInfo fieldInfo) {
            if (iteration > 0 && fieldInfo.name.equals("intSet")) {
                Assert.assertEquals(SIZE_EQUALS_2, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.SIZE));
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("SimpleSizeChecks", 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
