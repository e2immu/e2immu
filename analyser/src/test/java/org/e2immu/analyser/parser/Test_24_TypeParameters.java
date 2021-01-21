package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.TypeMapVisitor;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

public class Test_24_TypeParameters extends CommonTestRunner {
    public Test_24_TypeParameters() {
        super(true);
    }

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo collection = typeMap.get(Collection.class);
        Assert.assertNotNull(collection);
        MethodInfo stream = collection.typeInspection.get().methods().stream().filter(m -> m.name.equals("stream")).findAny().orElseThrow();
        Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, stream.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL));
    };

    @Test
    public void test_0() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.TypeParameters_0";
        final String STRINGS = TYPE+".strings";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("TypeParameters_0".equals(d.methodInfo().name) && STRINGS.equals(d.variableName())) {
                Assert.assertEquals("(instance type Stream<E>).map(new C()).collect(Collectors.toList())", d.currentValue().toString());
                Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
            }
        };

        testClass("TypeParameters_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("TypeParameters_1", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("TypeParameters_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("TypeParameters_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
