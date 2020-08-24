package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.analyser.NumberedStatement;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;

import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.StringValue;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/*

https://github.com/bnaudts/e2immu/issues/8

 */
public class TestSimpleNotNullChecks extends CommonTestRunner {
    public TestSimpleNotNullChecks() {
        super(false);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("a1".equals(d.variableName) && "0".equals(d.statementId)) {
            Assert.assertNull(d.properties.get(VariableProperty.NOT_NULL));
        }
        if ("s1".equals(d.variableName) && "0".equals(d.statementId)) {
            Assert.assertTrue(d.currentValue instanceof VariableValue);
            Assert.assertEquals("a1", ((VariableValue) d.currentValue).name);
        }
        if ("s1".equals(d.variableName) && "1.0.0".equals(d.statementId)) {
            Assert.assertTrue(d.currentValue instanceof StringValue);
        }
        if ("s1".equals(d.variableName) && "1".equals(d.statementId)) {
            Assert.assertTrue(d.currentValue instanceof VariableValue);
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method1".equals(d.methodInfo.name)) {
            if ("1.0.0".equals(d.statementId)) {
                Assert.assertEquals("null == a1", d.condition.toString());
            }
            if ("1".equals(d.statementId)) {
                Assert.assertNull(d.condition);
            }
        }
    };


    @Test
    public void test() throws IOException {
        testClass("SimpleNotNullChecks", 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
