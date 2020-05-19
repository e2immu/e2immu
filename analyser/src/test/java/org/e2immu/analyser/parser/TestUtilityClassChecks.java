package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Constant;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestUtilityClassChecks extends CommonTestRunner {
    public TestUtilityClassChecks() {
        super(false);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = (iteration, methodInfo, numberedStatement) -> {
        if ("print".equals(methodInfo.name)) {
            if ("0".equals(numberedStatement.streamIndices())) {
                Assert.assertTrue(numberedStatement.errorValue.get()); // potential null pointer exception
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("UtilityClassChecks", 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
