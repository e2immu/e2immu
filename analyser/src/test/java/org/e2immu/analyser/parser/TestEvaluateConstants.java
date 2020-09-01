package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.value.StringValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestEvaluateConstants extends CommonTestRunner {
    public TestEvaluateConstants() {
        super(false);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("print".equals(d.methodInfo.name)) {
            if ("0".equals(d.statementId)) {
                Assert.assertTrue(d.numberedStatement.errorValue.get()); // if conditional
            }
            if ("0.0.0".equals(d.statementId)) {
                Assert.assertTrue(d.numberedStatement.inErrorState());
                Assert.assertFalse(d.numberedStatement.errorValue.isSet());
            }
        }
        if ("print2".equals(d.methodInfo.name)) {
            if ("0".equals(d.statementId)) {
                Assert.assertTrue(d.numberedStatement.errorValue.get()); // inline conditional
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            if ("print".equals(methodInfo.name)) {
                Assert.assertTrue(methodInfo.methodAnalysis.get().singleReturnValue.isSet());
                Value singleReturnValue = methodInfo.methodAnalysis.get().singleReturnValue.get();
                Assert.assertSame(UnknownValue.RETURN_VALUE, singleReturnValue); // not constant, the ee() error is ignored
            }
            if ("print2".equals(methodInfo.name) && iteration > 0) {
                Assert.assertTrue(methodInfo.methodAnalysis.get().singleReturnValue.isSet());
                Value singleReturnValue = methodInfo.methodAnalysis.get().singleReturnValue.get();
                Assert.assertTrue(singleReturnValue instanceof StringValue); // inline conditional works as advertised
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("EvaluateConstants", 3, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());

        // second time, run with replacement

        testClass("EvaluateConstants", 3, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
