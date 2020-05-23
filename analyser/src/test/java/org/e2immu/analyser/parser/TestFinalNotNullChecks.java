package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.CombinedValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class TestFinalNotNullChecks extends CommonTestRunner {
    public TestFinalNotNullChecks() {
        super(false);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor =
            (iteration, methodInfo, statementId, variableName, variable, currentValue, properties) -> {
                if ("toString".equals(methodInfo.name) && "FinalNotNullChecks.this.input".equals(variableName)) {
                    if (iteration == 0) {
                        Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_NULL));
                    }
                    if (iteration == 1) {
                        // VV -> CombinedValue to freeze the properties
                        Assert.assertTrue(currentValue instanceof CombinedValue);
                        Assert.assertEquals(1, (int) properties.get(VariableProperty.NOT_NULL));
                    }
                }
                if ("debug".equals(methodInfo.name) && "java.lang.System.out".equals(variableName)) {
                    Assert.assertTrue(currentValue instanceof VariableValue);
                    Assert.assertEquals(1, (int) properties.get(VariableProperty.NOT_NULL));
                }
            };

    StatementAnalyserVisitor statementAnalyserVisitor = (iteration, methodInfo, numberedStatement) -> {
        if ("debug".equals(methodInfo.name) && "0".equals(numberedStatement.streamIndices())) {
            Assert.assertTrue(numberedStatement.errorValue.get());
        }
    };

    @Test
    public void test() throws IOException {
        testClass("FinalNotNullChecks", 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
