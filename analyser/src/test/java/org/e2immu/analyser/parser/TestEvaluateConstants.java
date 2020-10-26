package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
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
                Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
            }
            if ("0.0.0".equals(d.statementId)) {
                Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
            }
        }
        if ("print2".equals(d.methodInfo.name)) {
            if ("0".equals(d.statementId)) {
                Assert.assertNotNull(d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("print".equals(d.methodInfo().name)) {
            Value srv = d.methodAnalysis().getSingleReturnValue();
            Assert.assertSame(UnknownValue.RETURN_VALUE, srv); // not constant, the ee() error is ignored
        }
        if ("print2".equals(d.methodInfo().name) && d.iteration() > 0) {
            Value srv = d.methodAnalysis().getSingleReturnValue();
            Assert.assertTrue(srv instanceof StringValue); // inline conditional works as advertised
        }
    };

    @Test
    public void test() throws IOException {
        testClass("EvaluateConstants", 3, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());

        // second time, run with replacement

        testClass("EvaluateConstants", 2, 0, new DebugConfiguration.Builder()
                .build());
    }

}
