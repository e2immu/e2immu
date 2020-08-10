package org.e2immu.analyser.parserfailing;

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
import org.e2immu.analyser.parser.CommonTestRunner;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

/**
 * Method 3:
 * following the @NotNull from `res` (1) into the return statement summary (2) into the method's return value (3)
 * <p>
 * Method 1:
 *
 * https://github.com/bnaudts/e2immu/issues/12
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
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, tv.properties.get(VariableProperty.NOT_NULL)); // (2)
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, methodAnalysis.getProperty(VariableProperty.NOT_NULL)); // (3)
            }
            if ("method3bis".equals(methodInfo.name)) {
                TransferValue tv = methodAnalysis.returnStatementSummaries.get("2");
                Assert.assertNotNull(tv);
                Assert.assertEquals(MultiLevel.NULLABLE, tv.properties.get(VariableProperty.NOT_NULL));
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("method3bis".equals(methodInfo.name) && "1".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.isSet());
            }
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("method3".equals(d.methodInfo.name) && "res".equals(d.variableName)) {
            if ("2".equals(d.statementId)) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, (int) d.properties.get(VariableProperty.NOT_NULL)); // (1)
            }
        }
        if ("method1".equals(d.methodInfo.name) && "res1".equals(d.variableName)) {
            if ("2.0.0".equals(d.statementId)) {
                Assert.assertEquals(Level.TRUE, (int) d.properties.get(VariableProperty.ASSIGNED_IN_LOOP)); // (4)
                Assert.assertEquals(Level.TRUE, (int) d.properties.get(VariableProperty.LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED)); // (4)
                Assert.assertTrue(d.currentValue instanceof StringValue);
            }
            if ("3".equals(d.statementId)) {
                Assert.assertTrue(d.currentValue instanceof VariableValue);
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
