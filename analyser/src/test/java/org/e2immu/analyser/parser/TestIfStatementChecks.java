package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestIfStatementChecks extends CommonTestRunner {
    public TestIfStatementChecks() {
        super(false);
    }
    /*

     public static String method4(String c) {
        String res;
        if (c == null) {
            res = "abc";
        } else {
            res = "cef";
        }
        return res;
    }
     */

    StatementAnalyserVariableVisitor statementAnalyserVisitor = (iteration, methodInfo, statementId, variableName,
                                                                 variable, currentValue, properties) -> {
        if (methodInfo.name.equals("method4") && "res".equals(variableName)) {
            if ("0".equals(statementId)) {
                Assert.assertNull(properties.get(VariableProperty.CONTENT_MODIFIED));
                Assert.assertEquals(1, (int) properties.get(VariableProperty.CREATED));
                Assert.assertNull(properties.get(VariableProperty.READ)); // nothing that points to not null
            } else if ("1.0.0".equals(statementId) || "1.1.0".equals(statementId)) {
                Assert.assertEquals(1, (int) properties.get(VariableProperty.ASSIGNED));
                Assert.assertNull(properties.get(VariableProperty.READ)); // nothing that points to not null
            } else if ("1".equals(statementId)) {
                Assert.assertEquals(1, (int) properties.get(VariableProperty.ASSIGNED));
                Assert.assertNull(properties.get(VariableProperty.READ)); // nothing that points to not null
            } else if ("2".equals(statementId)) {
                Assert.assertEquals(1, (int) properties.get(VariableProperty.READ));
            } else Assert.fail("Statement " + statementId);
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {

        if ("method4".equals(methodInfo.name) && iteration >= 0) {

        }
    };

    @Test
    public void test() throws IOException {
        testClass("IfStatementChecks", 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
