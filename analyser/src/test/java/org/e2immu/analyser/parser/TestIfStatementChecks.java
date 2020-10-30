package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.InlineValue;
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
        if (d.methodInfo().name.equals("method4") && "res".equals(d.variableName())) {
            if ("0".equals(d.statementId())) {
                Assert.assertFalse(d.properties().isSet(VariableProperty.MODIFIED));
                Assert.assertFalse(d.properties().isSet(VariableProperty.READ)); // nothing that points to not null
            } else if ("1.0.0".equals(d.statementId()) || "1.1.0".equals(d.statementId())) {
                Assert.assertEquals(1, d.properties().get(VariableProperty.ASSIGNED));
                Assert.assertFalse(d.properties().isSet(VariableProperty.READ)); // nothing that points to not null
            } else if ("1".equals(d.statementId())) {
                Assert.assertEquals(1, d.properties().get(VariableProperty.ASSIGNED));
                Assert.assertFalse(d.properties().isSet(VariableProperty.READ)); // nothing that points to not null
            } else if ("2".equals(d.statementId())) {
                Assert.assertEquals(1, d.properties().get(VariableProperty.READ));
            } else Assert.fail("Statement " + d.statementId());
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method1".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                Assert.assertEquals("not (null == a)",
                        d.statementAnalysis().stateData.conditionManager.get().state.toString());
            }
        }
    };

    // inlining happens when the replacements are active
    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;
        if (d.iteration() > 0) {
            MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();
            if ("method1".equals(name)) {
                Value value = d.methodAnalysis().getSingleReturnValue();
                Assert.assertTrue("Got: " + value.getClass(), value instanceof InlineValue);
            }

            if ("method2".equals(name)) {
                Value value = d.methodAnalysis().getSingleReturnValue();
                Assert.assertTrue("Got: " + value.getClass(), value instanceof InlineValue);
            }

            if ("method3".equals(name)) {
                Value value = d.methodAnalysis().getSingleReturnValue();
                Assert.assertTrue("Got: " + value.getClass(), value instanceof InlineValue);
            }

            if ("method4".equals(name)) {
                Value value = d.methodAnalysis().getSingleReturnValue();
                // with more transformations, we can make this into an inline value TODO
                Assert.assertEquals("<return value>", value.toString());
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
