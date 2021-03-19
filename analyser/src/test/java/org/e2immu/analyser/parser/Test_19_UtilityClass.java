package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_19_UtilityClass extends CommonTestRunner {
    public Test_19_UtilityClass() {
        super(true);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("print".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                Assert.assertNull(d.haveError(Message.POTENTIAL_NULL_POINTER_EXCEPTION));
            }
        }
    };

    @Test
    public void test_0() throws IOException {
        testClass("UtilityClass_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }


    @Test
    public void test_1() throws IOException {
        testClass("UtilityClass_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }


    @Test
    public void test_2() throws IOException {
        testClass("UtilityClass_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
