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

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement) {
            if("method1".equals(methodInfo.name) && "1".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.isSet());
            }
            if("method1bis".equals(methodInfo.name) && "1".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.isSet());
            }
            if("method1bis".equals(methodInfo.name) && "2".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.isSet());
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
        testClass("SimpleSizeChecks", 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
