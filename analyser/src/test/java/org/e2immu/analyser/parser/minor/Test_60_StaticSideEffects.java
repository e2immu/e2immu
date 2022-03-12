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
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_60_StaticSideEffects extends CommonTestRunner {

    // AtomicInteger, System.out, ...
    public Test_60_StaticSideEffects() {
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
                        case 0 -> "null==<f:counter>";
                        case 1 -> "null==<f*:counter>";
                        default -> "null==StaticSideEffects_1.counter";
                    };
                    assertEquals(expected, d.evaluationResult().getExpression().toString());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("StaticSideEffects_1".equals(d.typeInfo().simpleName)) {
                assertEquals("Type param K", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("StaticSideEffects_1".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "null==<f:counter>";
                        case 1 -> "null==<f*:counter>";
                        default -> "null==StaticSideEffects_1.counter";
                    };
                    assertEquals(expected, d.condition().toString());
                    assertTrue(d.statementAnalysis().flowData().interruptsFlowIsSet());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("StaticSideEffects_1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "counter".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("new AtomicInteger()", d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    }
                    if ("1".equals(d.statementId())) {
                        String expectedValue =switch(d.iteration()) {
                            case 0 -> "null==<f:counter>?new AtomicInteger():<f:counter>";
                            case 1 -> "<wrapped:counter>"; // result of breaking delay in Merge
                            default -> "null==StaticSideEffects_1.counter?new AtomicInteger():nullable instance type AtomicInteger";
                        };
                        assertEquals(expectedValue, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    }
                    if ("2".equals(d.statementId())) {
                        String expectedValue =switch(d.iteration()) {
                            case 0 -> "<mmc:counter>";
                            case 1 -> "<wrapped:counter>"; // result of breaking delay in Merge
                            default -> "instance type AtomicInteger";
                        };
                        assertEquals(expectedValue, d.currentValue().toString());

                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    }
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("counter".equals(d.fieldInfo().name)) {
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                if (d.iteration() > 0) {
                    String expected = "new AtomicInteger(),null";
                    assertEquals(expected, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());
                }
            }
        };

        testClass("StaticSideEffects_1", 0, 0, new DebugConfiguration.Builder()
           //     .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
           //     .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
            //    .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
            //    .addStatementAnalyserVisitor(statementAnalyserVisitor)
            //    .addEvaluationResultVisitor(evaluationResultVisitor)
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
                assertEquals(MultiLevel.IGNORE_MODS_DV, d.fieldAnalysis().getProperty(Property.IGNORE_MODIFICATIONS));
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        testClass("StaticSideEffects_4", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }
}
