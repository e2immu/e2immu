package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;

import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.Level;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestModifiedThis extends CommonTestRunner {
    public TestModifiedThis() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("clearAndLog".equals(d.methodInfo.name) && "ParentClass".equals(d.methodInfo.typeInfo.simpleName) && "0".equals(d.statementId)) {
            Assert.assertEquals("ParentClass.this", d.variableName);
            Assert.assertEquals(Level.TRUE, (int) d.properties.get(VariableProperty.MODIFIED));
        }
        if ("clearAndLog".equals(d.methodInfo.name) && "ChildClass".equals(d.methodInfo.typeInfo.simpleName) && "0".equals(d.statementId)) {
            Assert.assertEquals("ChildClass.this", d.variableName);
            Assert.assertEquals(Level.TRUE, (int) d.properties.get(VariableProperty.MODIFIED));
        }
        if("clear".equals(d.methodInfo.name) && "InnerOfChild".equals(d.methodInfo.typeInfo.simpleName)) {
            Assert.assertEquals("ChildClass.this", d.variableName);
            Assert.assertEquals(Level.TRUE, (int) d.properties.get(VariableProperty.MODIFIED));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("ModifiedThis", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
