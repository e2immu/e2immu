package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.CombinedValue;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.StringValue;
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
                Assert.assertTrue("Got " + value0.getClass(), value0 instanceof Instance);
                Value value1 = methodAnalysis.returnStatementSummaries.get("0.1.0").value.get();
                Assert.assertTrue("Got " + value1.getClass(), value1 instanceof Constant);
                Value srt = methodAnalysis.singleReturnValue.get();
                Assert.assertTrue("Got " + srt.getClass(), srt instanceof MethodValue);
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
