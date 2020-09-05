package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.model.Constant;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.abstractvalue.StringConcat;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestTryStatementChecks extends CommonTestRunner {
    public TestTryStatementChecks() {
        super(false);
    }

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
            if (iteration == 0 && "method1".equals(methodInfo.name)) {
                Assert.assertEquals(3, methodAnalysis.returnStatementSummaries.size());
                Value value0 = methodAnalysis.returnStatementSummaries.get("0.0.0").value.get();
                Assert.assertTrue("Got " + value0.getClass(), value0 instanceof StringConcat);
                Value value1 = methodAnalysis.returnStatementSummaries.get("0.1.0").value.get();
                Assert.assertTrue("Got " + value1.getClass(), value1 instanceof Constant);
                Value srv = methodAnalysis.singleReturnValue.get();
                Assert.assertSame(UnknownValue.RETURN_VALUE, srv);
            }
            if ("method3".equals(methodInfo.name)) {
                Assert.assertEquals(1, methodAnalysis.uselessAssignments.size());
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("TryStatementChecks", 1, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
