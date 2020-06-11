package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.ConditionalValue;
import org.e2immu.analyser.model.abstractvalue.InlineValue;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
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
                Assert.assertNull(properties.get(VariableProperty.MODIFIED));
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

        if ("method1".equals(methodInfo.name)) {
            Value value = methodInfo.methodAnalysis.get().singleReturnValue.get();
            Assert.assertTrue("Got: " + value.getClass(), value instanceof InlineValue);
        }

        if ("method2".equals(methodInfo.name)) {
            Value value = methodInfo.methodAnalysis.get().singleReturnValue.get();
            Assert.assertTrue("Got: " + value.getClass(), value instanceof InlineValue);
        }

        if ("method3".equals(methodInfo.name)) {
            Value value = methodInfo.methodAnalysis.get().singleReturnValue.get();
            Assert.assertTrue("Got: " + value.getClass(), value instanceof InlineValue);
        }

        // TODO at some point we will hav an InlineValue here as well, we'll simply have to transform
        // the pattern X x; if(...) x= else x= into an inline definition
        if ("method4".equals(methodInfo.name)) {
            Value value = methodInfo.methodAnalysis.get().singleReturnValue.get();
            Assert.assertTrue("Got: " + value.getClass(), value instanceof MethodValue);
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
