package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

public class TestNotModified1Checks extends CommonTestRunner {

    public TestNotModified1Checks() {
        super(true);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = (iteration, methodInfo, numberedStatement, conditional) -> {

        // checks the 2 errors
        if ("useApply".equals(methodInfo.name) && Set.of("2", "3").contains(numberedStatement.streamIndices())) {
            Assert.assertTrue(numberedStatement.errorValue.isSet());
        }
    };

    @Test
    public void test() throws IOException {
        testClass("NotModified1Checks", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
