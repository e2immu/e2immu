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

package org.e2immu.analyser.parser.start;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_06_FinalNotNullChecks extends CommonTestRunner {

    public Test_06_FinalNotNullChecks() {
        super(true);
    }

    @Test
    public void test() throws IOException {

        final String INPUT = "org.e2immu.analyser.parser.start.testexample.FinalNotNullChecks.input";
        final String PARAM = "org.e2immu.analyser.parser.start.testexample.FinalNotNullChecks.FinalNotNullChecks(String):0:param";
        final String INPUT_DELAYED = "<f:input>";
        final String INPUT_INSTANCE = "instance type String";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("debug".equals(d.methodInfo().name) && INPUT.equals(d.variableName())) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                String expectValue = d.iteration() == 0 ? INPUT_DELAYED : INPUT_INSTANCE;
                assertEquals(expectValue, d.currentValue().toString());
            }
            if ("toString".equals(d.methodInfo().name) && INPUT.equals(d.variableName())) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                String expectValue = d.iteration() == 0 ? INPUT_DELAYED : INPUT_INSTANCE;
                assertEquals(expectValue, d.currentValue().toString());
            }
            if ("FinalNotNullChecks".equals(d.methodInfo().name)) {
                if (PARAM.equals(d.variableName())) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(NOT_NULL_EXPRESSION));
                    if (d.iteration() == 0) {
                        // only during the 1st iteration there is no @NotNull on the parameter, so there is a restriction
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(NOT_NULL_EXPRESSION));
                    }
                }
                // the variable has the value of param, which has received a @NotNull
                if (INPUT.equals(d.variableName())) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(NOT_NULL_EXPRESSION));

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
                DV notNull = d.getFieldAsVariable(input).getValue()
                        .getProperty(d.evaluationContext(), NOT_NULL_EXPRESSION, true);
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, notNull);
            }
            if ("debug".equals(d.methodInfo().name)) {
                FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
                if (d.iteration() == 0) {
                    assertEquals(INPUT_DELAYED, d.getFieldAsVariable(input).getValue().toString());
                    assertTrue(d.result().analysisStatus().isProgress());
                } else {
                    DV notNull = d.getFieldAsVariable(input).getProperty(CONTEXT_NOT_NULL);
                    assertEquals(MultiLevel.NULLABLE_DV, notNull);
                    assertEquals(INPUT_INSTANCE, d.getFieldAsVariable(input).getValue().toString());
                }
            }
            if ("toString".equals(d.methodInfo().name)) {
                FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
                if (d.iteration() == 0) {
                    assertEquals(INPUT_DELAYED, d.getFieldAsVariable(input).getValue().toString());
                    assertTrue(d.result().analysisStatus().isProgress());
                } else {
                    DV notNull = d.getFieldAsVariable(input).getProperty(CONTEXT_NOT_NULL);
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, notNull);
                    assertEquals(INPUT_INSTANCE, d.getFieldAsVariable(input).getValue().toString());
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo objects = typeMap.get(Objects.class);
            MethodInfo requireNonNull = objects.typeInspection.get().methods().stream().filter(mi -> mi.name.equals("requireNonNull") &&
                    1 == mi.methodInspection.get().getParameters().size()).findFirst().orElseThrow();
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                    requireNonNull.methodAnalysis.get().getProperty(NOT_NULL_EXPRESSION));
            assertEquals(DV.TRUE_DV, requireNonNull.methodAnalysis.get().getProperty(IDENTITY));
            ParameterInfo parameterInfo = requireNonNull.methodInspection.get().getParameters().get(0);
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                    parameterInfo.parameterAnalysis.get().getProperty(NOT_NULL_PARAMETER));
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            assertEquals("input", d.fieldInfo().name);
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));
            assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(FINAL));
            assertEquals("param/*@NotNull*/", d.fieldAnalysis().getValue().toString());
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if (d.methodInfo().name.equals("FinalNotNullChecks")) {
                FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
                VariableInfo vi = d.getFieldAsVariable(input);
                assert vi != null;
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_PARAMETER);

                Expression inputValue = vi.getValue();
                DV notNull = inputValue.getProperty(d.evaluationContext(), NOT_NULL_EXPRESSION, true);
                assertEquals("param/*@NotNull*/", inputValue.toString());
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, notNull);
            }
            if (d.methodInfo().name.equals("debug")) {
                FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
                VariableInfo vi = d.getFieldAsVariable(input);
                assert vi != null;
                String expectValue = d.iteration() == 0 ? INPUT_DELAYED : INPUT_INSTANCE;
                assertEquals(expectValue, vi.getValue().toString());
            }
            if (d.methodInfo().name.equals("toString")) {
                FieldInfo input = d.methodInfo().typeInfo.getFieldByName("input", true);
                VariableInfo vi = d.getFieldAsVariable(input);
                assert vi != null;
                String expectValue = d.iteration() == 0 ? INPUT_DELAYED : INPUT_INSTANCE;
                assertEquals(expectValue, vi.getValue().toString());
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, vi.getProperty(CONTEXT_NOT_NULL));
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
