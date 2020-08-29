package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestUtilityClassChecks extends CommonTestRunner {
    public TestUtilityClassChecks() {
        super(true);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("print".equals(d.methodInfo.name)) {
            if ("0".equals(d.statementId)) {
                Assert.assertFalse(d.numberedStatement.errorValue.isSet()); // no potential null pointer exception, because we know 'out'
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("UtilityClassChecks", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
