package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.ConstrainedNumericValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class TestNotModifiedChecks2 extends CommonTestRunner {
    public TestNotModifiedChecks2() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = new StatementAnalyserVariableVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, String statementId, String variableName, Variable variable, Value currentValue, Map<VariableProperty, Integer> properties) {
            if ("add".equals(methodInfo.name) && "theSet".equals(variableName)) {
                if("1".equals(statementId)) {
                    Assert.assertEquals(Level.FALSE, (int) properties.get(VariableProperty.MODIFIED));
                }
                if("2".equals(statementId)) {
                    Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.MODIFIED));
                }
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("NotModifiedChecks2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
