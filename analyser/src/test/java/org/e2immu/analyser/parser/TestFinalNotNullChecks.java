package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.FinalFieldValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Objects;

public class TestFinalNotNullChecks extends CommonTestRunner {
    public TestFinalNotNullChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("toString".equals(d.methodInfo.name) && "FinalNotNullChecks.this.input".equals(d.variableName)) {
            int notNull = d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL);
            if (d.iteration == 0) {
                Assert.assertTrue(d.currentValue instanceof UnknownValue);
                Assert.assertEquals(Level.FALSE, notNull);
            } else if (d.iteration == 1) {
                Assert.assertTrue(d.currentValue instanceof FinalFieldValue);
                Assert.assertEquals(Level.DELAY, notNull);
            } else {
                Assert.assertTrue(d.currentValue instanceof FinalFieldValue);
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
            }
        }
        if ("FinalNotNullChecks".equals(d.methodInfo.name) && "param".equals(d.variableName)) {
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
            if (d.iteration == 0) {
                // only during the 1st iteration there is no @NotNull on the parameter, so there is a restriction
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, (int) d.properties.get(VariableProperty.NOT_NULL));
            }
        }
        // the variable has the value of param, which has received a @NotNull
        if ("FinalNotNullChecks".equals(d.methodInfo.name) && "FinalNotNullChecks.this.input".equals(d.variableName)) {
            Assert.assertNull(d.properties.get(VariableProperty.NOT_NULL));
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo objects = typeContext.getFullyQualified(Objects.class);
        MethodInfo requireNonNull = objects.typeInspection.getPotentiallyRun().methods.stream().filter(mi -> mi.name.equals("requireNonNull") &&
                1 == mi.methodInspection.get().parameters.size()).findFirst().orElseThrow();
        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, requireNonNull.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL));
        Assert.assertEquals(Level.TRUE, requireNonNull.methodAnalysis.get().getProperty(VariableProperty.IDENTITY));
        ParameterInfo parameterInfo = requireNonNull.methodInspection.get().parameters.get(0);
        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = (iteration, fieldInfo) -> {
        if (iteration == 0 && "input".equals(fieldInfo.name)) {
            Assert.assertEquals(Level.DELAY, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.NOT_NULL));
        }
        if (iteration >= 2 && "input".equals(fieldInfo.name)) {
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.NOT_NULL));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if (methodInfo.name.equals("FinalNotNullChecks")) {
            ParameterInfo parameterInfo = methodInfo.methodInspection.get().parameters.get(0);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("FinalNotNullChecks", 0, 0, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
