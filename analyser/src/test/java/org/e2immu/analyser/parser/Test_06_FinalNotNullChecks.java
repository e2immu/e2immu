/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class Test_06_FinalNotNullChecks extends CommonTestRunner {

    public Test_06_FinalNotNullChecks() {
        super(true);
    }

    @Test
    public void test() throws IOException {

        final String INPUT = "org.e2immu.analyser.testexample.FinalNotNullChecks.input";
        final String PARAM = "org.e2immu.analyser.testexample.FinalNotNullChecks.FinalNotNullChecks(String):0:param";
        final String INPUT_DELAYED = "<f:input>";
        final String INPUT_INSTANCE = "instance type String";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("debug".equals(d.methodInfo().name) && INPUT.equals(d.variableName())) {
                int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectNN, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                int expectFinal = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                assertEquals(expectFinal, d.getProperty(VariableProperty.FINAL));
                String expectValue = d.iteration() == 0 ? INPUT_DELAYED : INPUT_INSTANCE;
                assertEquals(expectValue, d.currentValue().toString());
            }
            if ("toString".equals(d.methodInfo().name) && INPUT.equals(d.variableName())) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                int expectFinal = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                assertEquals(expectFinal, d.getProperty(VariableProperty.FINAL));
                String expectValue = d.iteration() == 0 ? INPUT_DELAYED : INPUT_INSTANCE;
                assertEquals(expectValue, d.currentValue().toString());
            }
            if ("FinalNotNullChecks".equals(d.methodInfo().name)) {
                if (PARAM.equals(d.variableName())) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    if (d.iteration() == 0) {
                        // only during the 1st iteration there is no @NotNull on the parameter, so there is a restriction
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                }
                // the variable has the value of param, which has received a @NotNull
                if (INPUT.equals(d.variableName())) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));

                    assertEquals("param/*@NotNull*/", d.variableInfoContainer()
                            .best(VariableInfoContainer.Level.EVALUATION).getValue().toString());
                    assertEquals("param/*@NotNull*/", d.currentValue().toString());
                    assertFalse(d.variableInfoContainer().hasMerge());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("FinalNotNullChecks".equals(d.methodInfo().name)) {
                FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
                int notNull = d.getFieldAsVariable(input).getValue()
                        .getProperty(d.evaluationContext(), VariableProperty.NOT_NULL_EXPRESSION, true);
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
            }
            if ("debug".equals(d.methodInfo().name)) {
                FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
                if (d.iteration() == 0) {
                    assertEquals(INPUT_DELAYED, d.getFieldAsVariable(input).getValue().toString());
                    assertEquals(AnalysisStatus.PROGRESS, d.result().analysisStatus());
                } else {
                    int notNull = d.getFieldAsVariable(input).getProperty(VariableProperty.CONTEXT_NOT_NULL);
                    assertEquals(MultiLevel.NULLABLE, notNull);
                    assertEquals(INPUT_INSTANCE, d.getFieldAsVariable(input).getValue().toString());
                }
            }
            if ("toString".equals(d.methodInfo().name)) {
                FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
                if (d.iteration() == 0) {
                    assertEquals(INPUT_DELAYED, d.getFieldAsVariable(input).getValue().toString());
                    assertEquals(AnalysisStatus.PROGRESS, d.result().analysisStatus());
                } else {
                    int notNull = d.getFieldAsVariable(input).getProperty(VariableProperty.CONTEXT_NOT_NULL);
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
                    assertEquals(INPUT_INSTANCE, d.getFieldAsVariable(input).getValue().toString());
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo objects = typeMap.get(Objects.class);
            MethodInfo requireNonNull = objects.typeInspection.get().methods().stream().filter(mi -> mi.name.equals("requireNonNull") &&
                    1 == mi.methodInspection.get().getParameters().size()).findFirst().orElseThrow();
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                    requireNonNull.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            assertEquals(Level.TRUE, requireNonNull.methodAnalysis.get().getProperty(VariableProperty.IDENTITY));
            ParameterInfo parameterInfo = requireNonNull.methodInspection.get().getParameters().get(0);
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                    parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL_PARAMETER));
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            assertEquals("input", d.fieldInfo().name);
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            assertEquals("param/*@NotNull*/", d.fieldAnalysis().getValue().toString());
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if (d.methodInfo().name.equals("FinalNotNullChecks")) {
                FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
                VariableInfo vi = d.getFieldAsVariable(input);
                assert vi != null;
                ParameterAnalysis param = d.parameterAnalyses().get(0);
                int expectParamCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectParamCnn, param.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                int expectOverallParameterNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectOverallParameterNotNull, param.getProperty(VariableProperty.NOT_NULL_PARAMETER));
                Expression inputValue = vi.getValue();
                int notNull = inputValue.getProperty(d.evaluationContext(), VariableProperty.NOT_NULL_EXPRESSION, true);
                assertEquals("param/*@NotNull*/", inputValue.toString());
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
            }
            if (d.methodInfo().name.equals("debug")) {
                FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
                VariableInfo vi = d.getFieldAsVariable(input);
                assert vi != null;
                String expectValue = d.iteration() == 0 ? INPUT_DELAYED : INPUT_INSTANCE;
                assertEquals(expectValue, vi.getValue().toString());
                int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectNN, vi.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
            if (d.methodInfo().name.equals("toString")) {
                FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
                VariableInfo vi = d.getFieldAsVariable(input);
                assert vi != null;
                String expectValue = d.iteration() == 0 ? INPUT_DELAYED : INPUT_INSTANCE;
                assertEquals(expectValue, vi.getValue().toString());
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, vi.getProperty(VariableProperty.CONTEXT_NOT_NULL));
            }
        };

        testClass("FinalNotNullChecks", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
