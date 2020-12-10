package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;

public class TestFunctionalInterfaceModified2 extends CommonTestRunner {

    public TestFunctionalInterfaceModified2() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("0".equals(d.statementId()) && "acceptMyCounter1".equals(d.methodInfo().name)) {
            if ("consumer".equals(d.variableName())) {
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
            }
            if ("FunctionalInterfaceModified2.this.myCounter1".equals(d.variableName())) {
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if (Set.of("acceptMyCounter1", "acceptMyCounter2", "acceptInt1").contains(d.methodInfo().name)) {
            Assert.assertTrue(d.methodAnalysis().methodLevelData()
                    .getCallsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod());
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if ("FunctionalInterfaceModified2".equals(d.typeInfo().name())) {
            Assert.assertEquals("[]", d.typeAnalysis().getImplicitlyImmutableDataTypes().toString());
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo consumer = typeMap.get(Consumer.class);
        MethodInfo accept = consumer.findUniqueMethod("accept", 1);
        Assert.assertEquals(Level.TRUE, accept.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
        ParameterInfo t = accept.methodInspection.get().getParameters().get(0);
        Assert.assertEquals(Level.TRUE, t.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
    };

    @Test
    public void test() throws IOException {
        testClass("FunctionalInterfaceModified2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

}
