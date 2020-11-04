package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_07_DependentVariables extends CommonTestRunner {
    public Test_07_DependentVariables() {
        super(false);
    }

    private final static String A2 = "org.e2immu.analyser.testexample.DependentVariables.method2(int):0:a";

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("method1".equals(d.methodInfo().name)) {

            int read = d.properties().getOrDefault(VariableProperty.READ, Level.DELAY);
            int assigned = d.properties().getOrDefault(VariableProperty.ASSIGNED, Level.DELAY);
            if ("1".equals(d.statementId()) && "array[0]".equals(d.variableName())) {
                Assert.assertEquals("12", d.currentValue().toString());
            }
            if ("2".equals(d.statementId()) && "array[0]".equals(d.variableName())) {
                Assert.assertTrue(assigned > read);
                Assert.assertEquals("12", d.variableInfo().getValue().toString());
            }
            if ("2".equals(d.statementId()) && "array[1]".equals(d.variableName())) {
                Assert.assertEquals("13", d.currentValue().toString());
            }
            if ("4".equals(d.statementId()) && "array[0]".equals(d.variableName())) {
                Assert.assertTrue(assigned < read);
                Assert.assertEquals("12", d.variableInfo().getValue().toString());
            }
            if ("4".equals(d.statementId()) && "array[1]".equals(d.variableName())) {
                Assert.assertTrue(assigned < read);
                Assert.assertEquals("13", d.variableInfo().getValue().toString());
            }
            if ("4".equals(d.statementId()) && "array[2]".equals(d.variableName())) {
                Assert.assertTrue(assigned < read);
                Assert.assertEquals("31", d.variableInfo().getValue().toString());
            }
            if ("4".equals(d.statementId()) && "array".equals(d.variableName())) {
                Assert.assertTrue(read > 1);
            }
        }
        if ("method2".equals(d.methodInfo().name) && "3".equals(d.statementId())) {
            if ("b".equals(d.variableName())) {
                Assert.assertEquals(A2, d.variableInfo().getValue().toString());
            }
            if ("array[b]".equals(d.variableName())) {
                Assert.fail("This variable should not be produced");
            }
            if (("array[" + A2 + "]").equals(d.variableName())) {
                Assert.assertEquals("12", d.variableInfo().getValue().toString());
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method1".equals(d.methodInfo().name) && "4".equals(d.statementId())) {
            VariableInfo tv = d.getReturnAsVariable();
            Assert.assertEquals("56", tv.getValue().toString());
        }
        if ("method2".equals(d.methodInfo().name) && "3".equals(d.statementId())) {
            VariableInfo tv = d.getReturnAsVariable();
            Assert.assertEquals("12", tv.getValue().toString());
        }
    };

    @Test
    public void test() throws IOException {
        testClass("DependentVariables", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
