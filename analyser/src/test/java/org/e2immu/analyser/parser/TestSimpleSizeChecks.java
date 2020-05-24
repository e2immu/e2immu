package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestSimpleSizeChecks extends CommonTestRunner {
    public TestSimpleSizeChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = (iteration, methodInfo, statementId, variableName,
                                                                         variable, currentValue, properties) -> {

    };

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement) {

        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = new FieldAnalyserVisitor() {
        @Override
        public void visit(int iteration, FieldInfo fieldInfo) {
            if (fieldInfo.name.equals("intSet")) {
                if (iteration == 0) {
                }
                if (iteration == 1) {

                }
            }
        }
    };


    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
            if ("method1".equals(methodInfo.name)) {
                TransferValue tv = methodAnalysis.returnStatementSummaries.get("2");
                Assert.assertNotNull(tv);
                Assert.assertEquals(Level.compose(Level.TRUE, Level.NOT_NULL_1), tv.properties.get(VariableProperty.NOT_NULL));
                Assert.assertEquals(Analysis.encodeSizeEquals(1), tv.properties.get(VariableProperty.SIZE)); // (1)
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("SimpleSizeChecks", 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
