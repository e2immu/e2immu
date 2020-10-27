package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.StringConcat;
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
                Assert.assertFalse(d.properties.isSet(VariableProperty.MODIFIED)); // no method was called on parameter s4
                Assert.assertEquals(1, d.properties.get(VariableProperty.READ)); // read 1x
                // there is an explicit @NotNull on the first parameter of debug
                Assert.assertFalse(d.properties.isSet(VariableProperty.NOT_NULL)); // nothing that points to not null
            } else Assert.fail();
        }

        if ("FinalChecks".equals(d.methodInfo.name) && d.methodInfo.methodInspection.get().parameters.size() == 2
                && "FinalChecks.this.s1".equals(d.variableName)) {
            if (d.iteration == 0) {
                Assert.assertSame(UnknownValue.NO_VALUE, d.currentValue);
            } else if (d.iteration == 1) {
                Assert.assertEquals("s1 + abc", d.currentValue.toString());
                Assert.assertEquals(MultiLevel.EFFECTIVE, MultiLevel.value(d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL), MultiLevel.NOT_NULL));
                Assert.assertFalse(d.properties.isSet(VariableProperty.NOT_NULL));
            } else if (d.iteration > 1) {
                Assert.assertEquals("s1 + abc", d.currentValue.toString());
                Assert.assertTrue(d.currentValue instanceof StringConcat);
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, MultiLevel.value(d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL), MultiLevel.NOT_NULL));
            }
        }

    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        TypeInfo stringType = d.evaluationContext().getAnalyserContext().getPrimitives().stringTypeInfo;
        Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, stringType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        int iteration = d.iteration();
        MethodInfo methodInfo = d.methodInfo();

        if ("setS4".equals(methodInfo.name) && iteration >= 1) {
            // @NotModified decided straight away, @Identity as well
            ParameterInfo s4 = methodInfo.methodInspection.get().parameters.get(0);
            Assert.assertEquals(Level.FALSE, s4.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
        }

        // there is no size restriction
        if (iteration > 0) {
            MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();
            FieldInfo s1 = methodInfo.typeInfo.typeInspection.getPotentiallyRun().fields.stream().filter(f -> "s1".equals(f.name)).findFirst().orElseThrow();
            if ("toString".equals(methodInfo.name)) {
                Assert.assertFalse(methodLevelData.fieldSummaries.get(s1).properties.isSet(VariableProperty.NOT_NULL));
            }
            if ("FinalChecks".equals(methodInfo.name) && methodInfo.methodInspection.get().parameters.size() == 1) {
                Assert.assertFalse(methodLevelData.fieldSummaries.get(s1).properties.isSet(VariableProperty.NOT_NULL));
            }
            if ("FinalChecks".equals(methodInfo.name) && methodInfo.methodInspection.get().parameters.size() == 2) {
                Assert.assertFalse(methodLevelData.fieldSummaries.get(s1).properties.isSet(VariableProperty.NOT_NULL));
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("FinalChecks", 0, 0, new DebugConfiguration.Builder()
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
