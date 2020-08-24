package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestExampleManualEventuallyE1Container extends CommonTestRunner {

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("setNegativeJ".equals(methodInfo.name)) {
            if (iteration > 0) {
                Assert.assertEquals("(-j >= 0 and -this.j >= 0)", methodInfo.methodAnalysis.get().precondition.get().toString());
                Assert.assertEquals("-this.j >= 0", methodInfo.methodAnalysis.get().preconditionForOnlyData.get().toString());
            }
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("setNegativeJ".equals(d.methodInfo.name) && "2".equals(d.statementId) && "ExampleManualEventuallyE1Container.this.j".equals(d.variableName)) {
            Assert.assertEquals("j", d.currentValue.toString());
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("setNegativeJ".equals(methodInfo.name)) {
                if ("0".equals(numberedStatement.streamIndices())) {
                    Assert.assertEquals("-j >= 0", conditional.toString());
                }
                if ("1".equals(numberedStatement.streamIndices()) && iteration > 0) {
                    // TODO!
                    Assert.assertEquals("..", conditional.toString());
                }
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("ExampleManualEventuallyE1Container", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
