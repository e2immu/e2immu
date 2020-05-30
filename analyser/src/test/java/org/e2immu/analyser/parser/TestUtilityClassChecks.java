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
        super(true);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = (iteration, methodInfo, numberedStatement, conditional) -> {
        if ("print".equals(methodInfo.name)) {
            if ("0".equals(numberedStatement.streamIndices())) {
                Assert.assertFalse(numberedStatement.errorValue.isSet()); // no potential null pointer exception, because we know 'out'
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
