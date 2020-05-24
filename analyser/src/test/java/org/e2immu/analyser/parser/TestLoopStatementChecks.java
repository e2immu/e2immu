package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class TestLoopStatementChecks extends CommonTestRunner {
    public TestLoopStatementChecks() {
        super(false);
    }

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
            if ("method6".equals(methodInfo.name)) {
                Assert.assertEquals(1, methodAnalysis.uselessAssignments.size());
                Assert.assertEquals(1, methodAnalysis.unusedLocalVariables.size());
            }
            if("method3".equals(methodInfo.name)) {
                TransferValue tv = methodAnalysis.returnStatementSummaries.get("2");
                Assert.assertNotNull(tv);
                Assert.assertEquals(Level.TRUE, tv.properties.get(VariableProperty.NOT_NULL));
            }
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = new StatementAnalyserVariableVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, String statementId, String variableName, Variable variable, Value currentValue, Map<VariableProperty, Integer> properties) {
            if ("method3".equals(methodInfo.name) && "res".equals(variableName)) {
                if ("2".equals(statementId)) {
                    Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_NULL));
                }
            }
        }
    };


    @Test
    public void test() throws IOException {
        testClass("LoopStatementChecks", 2, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
