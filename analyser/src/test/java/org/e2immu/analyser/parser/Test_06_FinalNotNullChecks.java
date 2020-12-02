package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.model.value.VariableValue;
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
            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
            Assert.assertEquals(expectNotNull, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
            if (d.iteration() == 0) {
                Assert.assertTrue(d.currentValue() instanceof UnknownValue);
            } else {
                Assert.assertTrue(d.currentValue() instanceof VariableValue);
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.FINAL));
            }
        }
        if ("FinalNotNullChecks".equals(d.methodInfo().name)) {
            if (PARAM.equals(d.variableName())) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
                if (d.iteration() == 0) {
                    // only during the 1st iteration there is no @NotNull on the parameter, so there is a restriction
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
                }
            }
            // the variable has the value of param, which has received a @NotNull
            if (INPUT.equals(d.variableName())) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));

                Assert.assertEquals("null", d.variableInfoContainer().best(VariableInfoContainer.LEVEL_1_INITIALISER).getValue().toString());
                Assert.assertEquals(PARAM_NN, d.variableInfoContainer().get(VariableInfoContainer.LEVEL_3_EVALUATION).getValue().toString());
                Assert.assertEquals(PARAM_NN, d.currentValue().toString());
                Assert.assertNull(d.variableInfoContainer().get(VariableInfoContainer.LEVEL_4_SUMMARY));
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("FinalNotNullChecks".equals(d.methodInfo().name)) {
            FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
            int notNull = d.getFieldAsVariable(input).getValue().getProperty(d.evaluationContext(), VariableProperty.NOT_NULL);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
        }
        if (("debug".equals(d.methodInfo().name) || "toString".equals(d.methodInfo().name))) {
            FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
            if (d.iteration() == 0) {
                Assert.assertSame(EmptyExpression.NO_VALUE, d.getFieldAsVariable(input).getValue());
                Assert.assertEquals(AnalysisStatus.PROGRESS, d.result().analysisStatus);
            } else {
                int notNull = d.getFieldAsVariable(input).getProperty(VariableProperty.NOT_NULL);
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
                Assert.assertEquals(INPUT, d.getFieldAsVariable(input).getValue().toString());
            }
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo objects = typeMap.get(Objects.class);
        MethodInfo requireNonNull = objects.typeInspection.get().methods().stream().filter(mi -> mi.name.equals("requireNonNull") &&
                1 == mi.methodInspection.get().getParameters().size()).findFirst().orElseThrow();
        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, requireNonNull.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL));
        Assert.assertEquals(Level.TRUE, requireNonNull.methodAnalysis.get().getProperty(VariableProperty.IDENTITY));
        ParameterInfo parameterInfo = requireNonNull.methodInspection.get().getParameters().get(0);
        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
        Assert.assertEquals("input", d.fieldInfo().name);
        Assert.assertEquals(expectNotNull, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL));
        Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
        if (d.iteration() == 0) {
            Assert.assertNull(d.fieldAnalysis().getEffectivelyFinalValue());
        } else {
            Assert.assertEquals(INPUT, d.fieldAnalysis().getEffectivelyFinalValue().toString());
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
        VariableInfo vi = d.getFieldAsVariable(input);
        if (d.methodInfo().name.equals("FinalNotNullChecks")) {
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.parameterAnalyses().get(0).getProperty(VariableProperty.NOT_NULL));
            Value inputValue = vi.getValue();
            int notNull = inputValue.getProperty(d.evaluationContext(), VariableProperty.NOT_NULL);
            Assert.assertEquals(PARAM_NN, inputValue.toString());
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
        }
        if ((d.methodInfo().name.equals("debug") || d.methodInfo().name.equals("toString"))) {
            if(d.iteration() == 0) {
                Assert.assertSame(EmptyExpression.NO_VALUE, vi.getValue());
            } else {
                Assert.assertEquals(INPUT, vi.getValue().toString());
            }
            int notNull = vi.getProperty(VariableProperty.NOT_NULL);
            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
            Assert.assertEquals(expectNotNull, notNull);
        }
    };

    @Test
    public void test() throws IOException {
        testClass("FinalNotNullChecks", 0, 0, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeMapVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
