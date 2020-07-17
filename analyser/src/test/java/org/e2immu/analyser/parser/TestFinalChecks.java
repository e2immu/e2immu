package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.FinalFieldValueObjectFlowInContext;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestFinalChecks extends CommonTestRunner {
    public TestFinalChecks() {
        super(false);
    }

    StatementAnalyserVariableVisitor statementAnalyserVisitor = d -> {
        if (d.methodInfo.name.equals("setS4") && "s4".equals(d.variableName)) {
            if ("0".equals(d.statementId)) {
                Assert.assertNull(d.properties.get(VariableProperty.MODIFIED)); // no method was called on parameter s4
                Assert.assertEquals(1, (int) d.properties.get(VariableProperty.READ)); // read 1x
                // there is an explicit @NotNull on the first parameter of debug
                Assert.assertNull(d.properties.get(VariableProperty.NOT_NULL)); // nothing that points to not null
            } else Assert.fail();
        }

        if ("FinalChecks".equals(d.methodInfo.name) && d.methodInfo.methodInspection.get().parameters.size() == 2
                && "FinalChecks.this.s1".equals(d.variableName)) {
            if (d.iteration == 0) {
                Assert.assertSame(UnknownValue.NO_VALUE, d.currentValue);
            } else if (d.iteration == 1) {
                Assert.assertEquals("instance type java.lang.String()", d.currentValue.toString());
                Assert.assertEquals(MultiLevel.EFFECTIVE, MultiLevel.value(d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL), MultiLevel.NOT_NULL));
                Assert.assertNull(d.properties.get(VariableProperty.NOT_NULL));
            } else if (d.iteration > 1) {
                Assert.assertEquals("s1", d.currentValue.toString());
                Assert.assertTrue(d.currentValue instanceof FinalFieldValueObjectFlowInContext);
                if (d.iteration == 2) {
                    Assert.assertEquals(MultiLevel.DELAY, MultiLevel.value(d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL), MultiLevel.NOT_NULL));
                } else {
                    Assert.assertEquals(MultiLevel.FALSE, MultiLevel.value(d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL), MultiLevel.NOT_NULL));
                }
            }
        }

    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        TypeInfo stringType = Primitives.PRIMITIVES.stringTypeInfo;
        Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, stringType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));

        if ("setS4".equals(methodInfo.name) && iteration >= 0) {
            // @NotModified decided straight away, @Identity as well
            ParameterInfo s4 = methodInfo.methodInspection.get().parameters.get(0);
            Assert.assertEquals(Level.FALSE, s4.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
        }

        // there is no size restriction
        if (iteration > 0) {
            FieldInfo s1 = methodInfo.typeInfo.typeInspection.get().fields.stream().filter(f -> "s1".equals(f.name)).findFirst().orElseThrow();
            if ("toString".equals(methodInfo.name)) {
                Assert.assertFalse(methodInfo.methodAnalysis.get().fieldSummaries.get(s1).properties.isSet(VariableProperty.NOT_NULL));
            }
            if ("FinalChecks".equals(methodInfo.name) && methodInfo.methodInspection.get().parameters.size() == 1) {
                Assert.assertFalse(methodInfo.methodAnalysis.get().fieldSummaries.get(s1).properties.isSet(VariableProperty.NOT_NULL));
            }
            if ("FinalChecks".equals(methodInfo.name) && methodInfo.methodInspection.get().parameters.size() == 2) {
                Assert.assertFalse(methodInfo.methodAnalysis.get().fieldSummaries.get(s1).properties.isSet(VariableProperty.NOT_NULL));
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("FinalChecks", 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test2() {
        String s1 = null;
        String s2 = null;
        String s3 = s1 + s2;
        Assert.assertEquals("nullnull", s3);
    }
}
