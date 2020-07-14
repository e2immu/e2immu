package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;

import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.StringValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

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

    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if("method1".equals(methodInfo.name)) {
                if ("1.0.0".equals(numberedStatement.streamIndices())) {
                    Assert.assertEquals("null == a1", conditional.toString());
                }
                if ("1".equals(numberedStatement.streamIndices())) {
                    Assert.assertNull(conditional);
                }
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
