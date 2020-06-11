package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.abstractvalue.InlineValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class TestInlineAndSizeChecks extends CommonTestRunner {
    public TestInlineAndSizeChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = new StatementAnalyserVariableVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, String statementId, String variableName, Variable variable, Value currentValue, Map<VariableProperty, Integer> properties) {
            if ("method1".equals(methodInfo.name) && "1".equals(statementId) && "l1".equals(variableName)) {
                Assert.assertEquals("in1.length(),?>=0", currentValue.toString());
            }
            // TODO for now, in2.toLowerCase().length() is not reduced to in2.length()
            if ("method2".equals(methodInfo.name) && "0".equals(statementId) && "l2".equals(variableName)) {
                Assert.assertEquals("in2.toLowerCase().length(),?>=0", currentValue.toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            if ("len".equals(methodInfo.name)) {
                Assert.assertEquals("null == s?(-1):s.length(),?>=0", methodInfo.methodAnalysis.get().singleReturnValue.get().toString());
            }

            if ("len6".equals(methodInfo.name)) {
                Assert.assertTrue(methodInfo.methodAnalysis.get().singleReturnValue.get() instanceof InlineValue);
            }
            if ("len7".equals(methodInfo.name)) {
                Assert.assertTrue(methodInfo.methodAnalysis.get().singleReturnValue.get() instanceof InlineValue);
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("InlineAndSizeChecks", 2, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
