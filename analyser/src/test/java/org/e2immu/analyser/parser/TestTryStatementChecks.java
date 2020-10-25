package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Constant;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.StringConcat;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestTryStatementChecks extends CommonTestRunner {
    public TestTryStatementChecks() {
        super(false);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method3".equals(d.methodInfo.name) && "1.1.1".equals(d.statementId)) {
            Assert.assertEquals("s", d.haveError(Message.USELESS_ASSIGNMENT));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();
        if (d.iteration() == 0 && "method1".equals(d.methodInfo().name)) {
            Assert.assertEquals(3, methodLevelData.returnStatementSummaries.size());
            Value value0 = methodLevelData.returnStatementSummaries.get("0.0.0").value.get();
            Assert.assertTrue("Got " + value0.getClass(), value0 instanceof StringConcat);
            Value value1 = methodLevelData.returnStatementSummaries.get("0.1.0").value.get();
            Assert.assertTrue("Got " + value1.getClass(), value1 instanceof Constant);
            Value srv = methodLevelData.singleReturnValue.get();
            Assert.assertSame(UnknownValue.RETURN_VALUE, srv);
        }
    };

    @Test
    public void test() throws IOException {
        testClass("TryStatementChecks", 1, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
