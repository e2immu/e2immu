package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.abstractvalue.FinalFieldValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TestFinalChecks extends CommonTestRunner {
    public TestFinalChecks() {
        super(false);
    }

    StatementAnalyserVariableVisitor statementAnalyserVisitor = (iteration, methodInfo, statementId, variableName,
                                                                 variable, currentValue, properties) -> {
        if (methodInfo.name.equals("setS4") && "s4".equals(variableName)) {
            if ("0".equals(statementId)) {
                Assert.assertNull(properties.get(VariableProperty.MODIFIED)); // no method was called on parameter s4
                Assert.assertEquals(1, (int) properties.get(VariableProperty.READ)); // read 1x
                // there is an explicit @NotNull on the first parameter of debug
                Assert.assertNull(properties.get(VariableProperty.NOT_NULL)); // nothing that points to not null
            } else Assert.fail();
        }

        if ("FinalChecks".equals(methodInfo.name) && methodInfo.methodInspection.get().parameters.size() == 2
                && "FinalChecks.this.s1".equals(variableName)) {
            if (iteration == 0) {
                Assert.assertSame(UnknownValue.NO_VALUE, currentValue);
            } else if (iteration == 1) {
                Assert.assertEquals("instance type java.lang.String()", currentValue.toString());
                Assert.assertEquals(Level.TRUE, Level.value(currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL), Level.NOT_NULL));
                Assert.assertNull(properties.get(VariableProperty.NOT_NULL));
            } else if (iteration > 1) {
                Assert.assertEquals("s1", currentValue.toString());
                Assert.assertTrue(currentValue instanceof FinalFieldValue);
                Assert.assertEquals(Level.DELAY, Level.value(currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL), Level.NOT_NULL));
            }
        }

    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        TypeInfo stringType = Primitives.PRIMITIVES.stringTypeInfo;
        Assert.assertEquals(VariableProperty.IMMUTABLE.best, stringType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));

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
