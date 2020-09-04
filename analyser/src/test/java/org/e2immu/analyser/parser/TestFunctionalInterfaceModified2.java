package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.function.Consumer;

public class TestFunctionalInterfaceModified2 extends CommonTestRunner {

    public TestFunctionalInterfaceModified2() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("0".equals(d.statementId) && "acceptMyCounter1".equals(d.methodInfo.name)) {
            if ("consumer".equals(d.variableName)) {
                Assert.assertEquals(Level.TRUE, (int) d.properties.get(VariableProperty.MODIFIED));
            }
            if ("FunctionalInterfaceModified2.this.myCounter1".equals(d.variableName)) {
                Assert.assertEquals(Level.TRUE, (int) d.properties.get(VariableProperty.MODIFIED));
            }
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo consumer = typeContext.getFullyQualified(Consumer.class);
        MethodInfo accept = consumer.findUniqueMethod("accept", 1);
        Assert.assertEquals(Level.TRUE, accept.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
        ParameterInfo t = accept.methodInspection.get().parameters.get(0);
        Assert.assertEquals(Level.TRUE, t.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
    };

    @Test
    public void test() throws IOException {
        testClass("FunctionalInterfaceModified2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}
