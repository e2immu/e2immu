package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.InlineValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
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

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if (d.methodInfo.name.equals("method4") && "res".equals(d.variableName)) {
            if ("0".equals(d.statementId)) {
                Assert.assertNull(d.properties.get(VariableProperty.MODIFIED));
                Assert.assertNull(d.properties.get(VariableProperty.READ)); // nothing that points to not null
            } else if ("1.0.0".equals(d.statementId) || "1.1.0".equals(d.statementId)) {
                Assert.assertEquals(1, (int) d.properties.get(VariableProperty.ASSIGNED));
                Assert.assertNull(d.properties.get(VariableProperty.READ)); // nothing that points to not null
            } else if ("1".equals(d.statementId)) {
                Assert.assertEquals(1, (int) d.properties.get(VariableProperty.ASSIGNED));
                Assert.assertNull(d.properties.get(VariableProperty.READ)); // nothing that points to not null
            } else if ("2".equals(d.statementId)) {
                Assert.assertEquals(1, (int) d.properties.get(VariableProperty.READ));
            } else Assert.fail("Statement " + d.statementId);
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method1".equals(d.methodInfo.name)) {
            if ("0".equals(d.statementId)) {
                Assert.assertEquals("not (null == a)", d.numberedStatement.state.get().toString());
            }
        }
    };

    // inlining happens when the replacements are active
    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if (iteration > 0) {
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

            if ("method4".equals(methodInfo.name)) {
                Value value = methodInfo.methodAnalysis.get().singleReturnValue.get();
                Assert.assertEquals("inline method4 on res", value.toString());
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("IfStatementChecks", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());

        testClass("IfStatementChecks", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
