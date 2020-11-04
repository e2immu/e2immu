package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Objects;

public class Test_06_FinalNotNullChecks extends CommonTestRunner {
    public Test_06_FinalNotNullChecks() {
        super(true);
    }

    private static final String INPUT = "org.e2immu.analyser.testexample.FinalNotNullChecks.input";
    private static final String PARAM = "org.e2immu.analyser.testexample.FinalNotNullChecks.FinalNotNullChecks(String):0:param";
    private static final String PARAM_NN = PARAM + ",@NotNull";

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if (("debug".equals(d.methodInfo().name) || "toString".equals(d.methodInfo().name)) && INPUT.equals(d.variableName())) {
            int notNull = d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL);
            if (d.iteration() == 0) {
                Assert.assertTrue(d.currentValue() instanceof UnknownValue);
                Assert.assertEquals(Level.FALSE, notNull);
            } else {
                Assert.assertTrue(d.currentValue() instanceof VariableValue);
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.FINAL));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
            }
        }
        if ("FinalNotNullChecks".equals(d.methodInfo().name) && PARAM.equals(d.variableName())) {
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
            if (d.iteration() == 0) {
                // only during the 1st iteration there is no @NotNull on the parameter, so there is a restriction
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.properties().get(VariableProperty.NOT_NULL));
            }
        }
        // the variable has the value of param, which has received a @NotNull
        if ("FinalNotNullChecks".equals(d.methodInfo().name) && INPUT.equals(d.variableName())) {
            Assert.assertFalse(d.properties().isSet(VariableProperty.NOT_NULL));

            Assert.assertEquals("null", d.variableInfo().initialValue.get().toString());
            Assert.assertEquals(PARAM_NN, d.variableInfo().expressionValue.get().toString());
            Assert.assertEquals(PARAM_NN, d.currentValue().toString());
            Assert.assertFalse(d.variableInfo().endValue.isSet());
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("FinalNotNullChecks".equals(d.methodInfo().name)) {
            MethodLevelData methodLevelData = d.statementAnalysis().methodLevelData;
            FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
            int notNull = d.getFieldAsVariable(input).valueForNextStatement().getProperty(d.evaluationContext(), VariableProperty.NOT_NULL);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
        }
        if (("debug".equals(d.methodInfo().name) || "toString".equals(d.methodInfo().name))) {
            MethodLevelData methodLevelData = d.statementAnalysis().methodLevelData;
            FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
            Assert.assertSame(UnknownValue.NO_VALUE, d.getFieldAsVariable(input).valueForNextStatement());
            if (d.iteration() == 0) {
                Assert.assertEquals(AnalysisStatus.PROGRESS, d.result().analysisStatus);
            } else {
                int notNull = d.getFieldAsVariable(input).getProperty(VariableProperty.NOT_NULL);
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
            }
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

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        String name = d.fieldInfo().name;
        if (d.iteration() == 0 && "input".equals(name)) {
            Assert.assertEquals(Level.DELAY, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL));
        }
        if (d.iteration() >= 1 && "input".equals(name)) {
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
        VariableInfo vi = d.getFieldAsVariable(input);
        if (d.methodInfo().name.equals("FinalNotNullChecks")) {
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.parameterAnalyses().get(0).getProperty(VariableProperty.NOT_NULL));
            Value inputValue = vi.valueForNextStatement();
            int notNull = inputValue.getProperty(d.evaluationContext(), VariableProperty.NOT_NULL);
            Assert.assertEquals(PARAM_NN, inputValue.toString());
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
        }
        if ((d.methodInfo().name.equals("debug") || d.methodInfo().name.equals("toString"))) {
            Assert.assertSame(UnknownValue.NO_VALUE, vi.valueForNextStatement());
            int notNull = vi.getProperty(VariableProperty.NOT_NULL);
            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
            Assert.assertEquals(expectNotNull, notNull);
        }
    };

    @Test
    public void test() throws IOException {
        testClass("FinalNotNullChecks", 0, 0, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
