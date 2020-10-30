package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.StringValue;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * Method 3:
 * following the @NotNull from `res` (1) into the return statement summary (2) into the method's return value (3)
 * <p>
 * Method 1:
 * <p>
 * https://github.com/bnaudts/e2immu/issues/12
 */
public class TestLoopStatementChecks extends CommonTestRunner {
    public TestLoopStatementChecks() {
        super(false);
    }

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("method3".equals(d.methodInfo().name)) {
            TransferValue tv = d.methodAnalysis().methodLevelData().returnStatementSummaries.get("2");
            Assert.assertNotNull(tv);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, tv.properties.get(VariableProperty.NOT_NULL)); // (2)
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL)); // (3)
        }
        if ("method3bis".equals(d.methodInfo().name)) {
            TransferValue tv = d.methodAnalysis().methodLevelData().returnStatementSummaries.get("2");
            Assert.assertNotNull(tv);
            Assert.assertEquals(MultiLevel.NULLABLE, tv.properties.get(VariableProperty.NOT_NULL));
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method3bis".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
        }
        if ("method6".equals(d.methodInfo().name) && "1.0.0".equals(d.statementId())) {
            Assert.assertNotNull(d.haveError(Message.USELESS_ASSIGNMENT));
            Assert.assertNotNull(d.haveError(Message.UNUSED_LOCAL_VARIABLE));
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("method3".equals(d.methodInfo().name) && "res".equals(d.variableName())) {
            if ("2".equals(d.statementId())) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, (int) d.properties().get(VariableProperty.NOT_NULL)); // (1)
            }
        }
        if ("method1".equals(d.methodInfo().name) && "res1".equals(d.variableName())) {
            if ("2.0.0".equals(d.statementId())) {
                Assert.assertEquals(Level.TRUE, (int) d.properties().get(VariableProperty.ASSIGNED_IN_LOOP)); // (4)
                Assert.assertEquals(Level.TRUE, (int) d.properties().get(VariableProperty.LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED)); // (4)
                Assert.assertTrue(d.currentValue() instanceof StringValue);
            }
            if ("3".equals(d.statementId())) {
                Assert.assertTrue(d.currentValue() instanceof VariableValue);
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("LoopStatementChecks", 3, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
