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

package org.e2immu.analyser.parser.minor;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_60_StaticSideEffects_AAPI extends CommonTestRunner {

    // AtomicInteger, System.out, ...
    public Test_60_StaticSideEffects_AAPI() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("StaticSideEffects_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("StaticSideEffects_1".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1 -> "<null-check>";
                        case 2, 3 -> "null==<vp:counter:link@Field_counter>";
                        default -> "null==StaticSideEffects_1.counter";
                    };
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("StaticSideEffects_1".equals(d.typeInfo().simpleName)) {
                assertHc(d, 0, "K");
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("StaticSideEffects_1".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1 -> "<null-check>";
                        case 2, 3 -> "null==<vp:counter:link@Field_counter>";
                        default -> "null==StaticSideEffects_1.counter";
                    };
                    assertEquals(expected, d.condition().toString());
                    assertTrue(d.statementAnalysis().flowData().interruptsFlowIsSet());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("CM{parent=CM{}}", d.statementAnalysis().stateData().getConditionManagerForNextStatement().toString());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals("true", d.state().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("StaticSideEffects_1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "k".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.scopeIsThis());
                    if ("0".equals(d.statementId())) {
                        assertEquals("k", d.currentValue().toString());
//FIXME                        assertDv(d, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "counter".equals(fr.fieldInfo.name)) {
                    assertNotEquals("0", d.statementId());
                    assertEquals("StaticSideEffects_1", fr.scope.toString());

                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("new AtomicInteger()", d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    }
                    if ("1".equals(d.statementId())) {
                        String expectedValue = switch (d.iteration()) {
                            case 0 -> "<null-check>?new AtomicInteger():<f:counter>";
                            case 1 -> "<wrapped:counter>"; // result of breaking delay in Merge
                            case 2, 3 -> "null==<vp:counter:link@Field_counter>?new AtomicInteger():<vp:counter:link@Field_counter>";
                            default -> "null==nullable instance type AtomicInteger?new AtomicInteger():nullable instance type AtomicInteger";
                        };
                        assertEquals(expectedValue, d.currentValue().toString());
                        assertDv(d, 4, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    }
                    if ("2".equals(d.statementId())) {
                        String expectedValue = switch (d.iteration()) {
                            case 0 -> "<null-check>?new AtomicInteger():<f:counter>";
                            case 1, 2, 3 -> "<wrapped:counter>"; // result of breaking delay in Merge
                            default -> "instance type AtomicInteger";
                        };
                        assertEquals(expectedValue, d.currentValue().toString());

                        // important! (see SAApply) the value properties do not change
                        // they are the cause of the potential null pointer exception that we still need to get rid of.
                        assertDv(d, 4, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                        assertDv(d, 4, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("counter".equals(d.fieldInfo().name)) {
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d, 3, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                assertDv(d, 2, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);

                if (d.iteration() > 0) {
                    String expected = "new AtomicInteger(),null";
                    assertEquals(expected, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());
                }
            }
            if ("k".equals(d.fieldInfo().name)) {
                assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                String expected = d.iteration() == 0 ? "<f:k>" : "k";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
                assertEquals("k:0", d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("StaticSideEffects_1".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 5, MultiLevel.NULLABLE_DV, Property.NOT_NULL_PARAMETER);
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                String assigned = d.iteration() <= 4 ? "assign_to_field@Parameter_k" : "";
                assertEquals(assigned, p0.assignedToFieldDelays().toString());
                assertEquals(d.iteration() >= 5, p0.assignedToFieldDelays().isDone());
            }
        };

        testClass("StaticSideEffects_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("StaticSideEffects_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("StaticSideEffects_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_4() throws IOException {

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("counter".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.IGNORE_MODS_DV, d.fieldAnalysis().getProperty(Property.EXTERNAL_IGNORE_MODIFICATIONS));
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        testClass("StaticSideEffects_4", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_5() throws IOException {
        testClass("StaticSideEffects_5", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_6() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if (d.methodInfo().isConstructor && n == 1) {
                if (d.variable() instanceof FieldReference fr && "generator".equals(fr.fieldInfo.name)) {
                    assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    assertCurrentValue(d, 1, "instance type AtomicInteger");
                }
            }
            if (d.methodInfo().isConstructor && n == 0) {
                if (d.variable() instanceof FieldReference fr && "generator".equals(fr.fieldInfo.name)) {
                    assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertCurrentValue(d, 1, "instance type AtomicInteger");
                }
            }
        };
        testClass("StaticSideEffects_6", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
