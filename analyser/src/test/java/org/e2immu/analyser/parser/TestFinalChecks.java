package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.ParameterInfo;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TestFinalChecks extends CommonTestRunner {
    public TestFinalChecks() {
        super(false);
    }
    /*

    public void setS4(String s4) {
        this.s4 = s4;
    }

     */

    StatementAnalyserVariableVisitor statementAnalyserVisitor = (iteration, methodInfo, statementId, variableName,
                                                                 variable, currentValue, properties) -> {
        if (methodInfo.name.equals("setS4") && "s4".equals(variableName)) {
            if ("0".equals(statementId)) {
                Assert.assertNull(properties.get(VariableProperty.CONTENT_MODIFIED)); // no method was called on parameter s4
                Assert.assertEquals(1, (int) properties.get(VariableProperty.READ)); // read 1x
                // there is an explicit @NotNull on the first parameter of debug
                Assert.assertNull(properties.get(VariableProperty.IN_NOT_NULL_CONTEXT)); // nothing that points to not null
            } else Assert.fail();
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("setS4".equals(methodInfo.name) && iteration >= 0) {
            // @NotModified decided straight away, @Identity as well
            ParameterInfo s4 = methodInfo.methodInspection.get().parameters.get(0);
            Assert.assertEquals(1, s4.parameterAnalysis.get().getProperty(VariableProperty.NOT_MODIFIED));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("FinalChecks", 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
