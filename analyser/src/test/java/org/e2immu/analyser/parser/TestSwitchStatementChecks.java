package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestSwitchStatementChecks extends CommonTestRunner {
    public TestSwitchStatementChecks() {
        super(false);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("method7".equals(methodInfo.name)) {
                if ("2".equals(numberedStatement.streamIndices())) {
                    Assert.assertTrue(numberedStatement.errorValue.get());
                }
                if ("2.2.0".equals(numberedStatement.streamIndices())) {
                    Assert.assertTrue(numberedStatement.errorValue.get());
                }
            }
            if ("method3".equals(methodInfo.name) && "0.2.0".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.get()); // method evaluates to constant
            }
            if ("method3".equals(methodInfo.name) && "0.2.0.0.0".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.get()); // unreachable statement
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            if ("method3".equals(methodInfo.name)) {
                // @NotNull annotation missing (the return statement is not marked "unreachable", maybe we should do this???)
                Assert.assertEquals(Level.DELAY, methodInfo.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL));
            }
            if ("method7".equals(methodInfo.name)) {
                // @Constant annotation missing, but is marked as constant
                Assert.assertEquals(Level.TRUE, methodInfo.methodAnalysis.get().getProperty(VariableProperty.CONSTANT));
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("SwitchStatementChecks", 6, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
