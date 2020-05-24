package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.StringValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

/**
 * Method 3:
 * following the @NotNull from `res` (1) into the return statement summary (2) into the method's return value (3)
 *
 * Method 1:
 *
 */
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
            if ("method3".equals(methodInfo.name)) {
                TransferValue tv = methodAnalysis.returnStatementSummaries.get("2");
                Assert.assertNotNull(tv);
                Assert.assertEquals(Level.TRUE, tv.properties.get(VariableProperty.NOT_NULL)); // (2)
                Assert.assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.NOT_NULL)); // (3)
            }
            if ("method3bis".equals(methodInfo.name)) {
                TransferValue tv = methodAnalysis.returnStatementSummaries.get("2");
                Assert.assertNotNull(tv);
                Assert.assertEquals(Level.FALSE, tv.properties.get(VariableProperty.NOT_NULL));
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement) {
            if ("method3bis".equals(methodInfo.name) && "1".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.isSet());
            }
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = new StatementAnalyserVariableVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, String statementId, String variableName, Variable variable, Value currentValue, Map<VariableProperty, Integer> properties) {
            if ("method3".equals(methodInfo.name) && "res".equals(variableName)) {
                if ("2".equals(statementId)) {
                    Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_NULL)); // (1)
                }
            }
            if("method1".equals(methodInfo.name) && "res1".equals(variableName)) {
                if("2.0.0".equals(statementId)) {
                    Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.ASSIGNED_IN_LOOP)); // (4)
                    Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED)); // (4)
                    Assert.assertTrue(currentValue instanceof StringValue);
                }
                if("3".equals(statementId)) {
                    Assert.assertTrue(currentValue instanceof VariableValue);
                }
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("LoopStatementChecks", 3, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
