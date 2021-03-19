package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.e2immu.analyser.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.function.Consumer;

public class Test_42_AbstractTypeAsParameter extends CommonTestRunner {

    public Test_42_AbstractTypeAsParameter() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("forEach".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "consumer".equals(p.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, d.getProperty(VariableProperty.IMMUTABLE));

                        int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                        Assert.assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                    if ("0".equals(d.statementId())) {
                        int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                        Assert.assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
            }
        };
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo consumer = typeMap.get(Consumer.class);
            MethodInfo accept = consumer.findUniqueMethod("accept", 1);
            Assert.assertEquals(Level.FALSE, accept.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
        };

        testClass("AbstractTypeAsParameter_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("AbstractTypeAsParameter_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("AbstractTypeAsParameter_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("AbstractTypeAsParameter_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_4() throws IOException {
        testClass("AbstractTypeAsParameter_4", 1, 0, new DebugConfiguration.Builder()
                .build());
    }
}
