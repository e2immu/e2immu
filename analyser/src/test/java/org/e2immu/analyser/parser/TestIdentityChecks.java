package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TestIdentityChecks extends CommonTestRunner {
    public TestIdentityChecks() {
        super(true);
    }

    /*

    @Identity
    @NotModified
    public static String idem(String s) {
        LOGGER.debug(s);
        return s;
    }

     */

    StatementAnalyserVariableVisitor statementAnalyserVisitor = (iteration, methodInfo, statementId, variableName,
                                                                 variable, currentValue, properties) -> {
        if (methodInfo.name.equals("idem") && "s".equals(variableName)) {
            if ("0".equals(statementId)) {
                Assert.assertEquals(2, (int) properties.get(VariableProperty.NOT_MODIFIED));
                Assert.assertEquals(1, (int) properties.get(VariableProperty.READ)); // read 2x
                // there is an explicit @NotNull on the first parameter of debug
                Assert.assertEquals(3, (int) properties.get(VariableProperty.NOT_NULL));
            } else if ("1".equals(statementId)) {
                Assert.assertEquals(2, (int) properties.get(VariableProperty.NOT_MODIFIED));
                Assert.assertEquals(3, (int) properties.get(VariableProperty.READ)); // read 2x
                // there is an explicit @NotNull on the first parameter of debug
                Assert.assertEquals(3, (int) properties.get(VariableProperty.NOT_NULL));
            } else Assert.fail();
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("idem".equals(methodInfo.name) && iteration >= 0) {
            // @NotModified decided straight away, @Identity as well
            Assert.assertEquals(1, methodInfo.methodAnalysis.get().getProperty(VariableProperty.NOT_MODIFIED));
            Assert.assertEquals(1, methodInfo.methodAnalysis.get().getProperty(VariableProperty.IDENTITY));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("IdentityChecks", 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
