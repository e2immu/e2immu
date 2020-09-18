package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestInnerClass extends CommonTestRunner {
    public TestInnerClass() {
        super(false);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("InnerClass".equals(d.methodInfo.name) && "0.0.0".equals(d.statementId) && "outerField".equals(d.variableName)) {
            Assert.assertTrue(d.variable instanceof ParameterInfo);
            int notNull = d.properties.getOrDefault(VariableProperty.NOT_NULL, Level.DELAY);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
        }
    };

    @Test
    public void test() throws IOException {
        testClass("InnerClass", 5, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
