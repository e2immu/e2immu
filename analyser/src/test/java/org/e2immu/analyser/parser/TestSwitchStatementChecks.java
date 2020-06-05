package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class TestSwitchStatementChecks extends CommonTestRunner {
    public TestSwitchStatementChecks() {
        super(false);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = new StatementAnalyserVariableVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, String statementId, String variableName, Variable variable, Value currentValue, Map<VariableProperty, Integer> properties) {
            if ("method7".equals(methodInfo.name) && "3".equals(statementId) && "res".equals(variableName)) {
                Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_NULL));
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("method7".equals(methodInfo.name)) {
                if ("2".equals(numberedStatement.streamIndices())) {
                    Assert.assertTrue(numberedStatement.errorValue.get());
                }
                if ("2.2.0".equals(numberedStatement.streamIndices())) {
                    Assert.assertTrue(numberedStatement.inErrorState());
                    Assert.assertFalse(numberedStatement.errorValue.isSet());
                }
            }
            if ("method3".equals(methodInfo.name) && "0.2.0".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.get()); // method evaluates to constant
            }
            if ("method3".equals(methodInfo.name) && "0.2.0.0.0".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.inErrorState());
                Assert.assertFalse(numberedStatement.errorValue.isSet());
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
                Assert.assertEquals(Level.TRUE, methodInfo.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL));
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("SwitchStatementChecks", 4, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
