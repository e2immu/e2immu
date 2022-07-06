
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

package org.e2immu.analyser.parser.basics;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_00_Basics_0 extends CommonTestRunner {
    private static final String TYPE = "org.e2immu.analyser.parser.basics.testexample.Basics_0";

    public Test_00_Basics_0() {
        super(false);
    }

    @Test
    public void test() throws IOException {
        // value only comes in second iteration
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if (d.methodInfo().name.equals("getExplicitlyFinal") && "0".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<f:explicitlyFinal>" : "\"abc\"";
                assertEquals(expectValue, d.evaluationResult().value().toString());

                assertEquals(d.iteration() == 0, d.evaluationResult().causesOfDelay().isDelayed());
            }
        };

        // everything in first iteration
        FieldAnalyserVisitor afterFieldAnalyserVisitor = d -> {
            FieldAnalysis fieldAnalysis = d.fieldAnalysis();
            if ("explicitlyFinal".equals(d.fieldInfo().name)) {
                assertEquals("", fieldAnalysis.getLinkedVariables().toString());
                assertEquals(DV.TRUE_DV, fieldAnalysis.getProperty(Property.FINAL));
                assertEquals("\"abc\"", fieldAnalysis.getValue().toString());
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(fieldAnalysis.getValue(),
                        NOT_NULL_EXPRESSION));
                assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, fieldAnalysis.getProperty(EXTERNAL_IMMUTABLE));
                assertTrue(fieldAnalysis.getLinkedVariables().isEmpty());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if (d.methodInfo().name.equals("getExplicitlyFinal") && "0".equals(d.statementId())) {
                assertTrue(d.condition().isBoolValueTrue());
                assertTrue(d.state().isBoolValueTrue());
                assertTrue(d.localConditionManager().precondition().isEmpty());
                mustSeeIteration(d, 1);
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.methodInfo().name.equals("getExplicitlyFinal") && "0".equals(d.statementId())) {
                // the field
                if ((TYPE + ".explicitlyFinal").equals(d.variableName())) {
                    assertFalse(d.variableInfo().isAssigned());
                    assertTrue(d.variableInfo().isRead());
                    if (d.iteration() == 0) {
                        assertTrue(d.currentValue().isDelayed());
                    } else {
                        assertEquals(new StringConstant(d.context().getPrimitives(), "abc"), d.currentValue());
                    }
                    assertDvInitial(d, "ext_not_null@Field_explicitlyFinal",
                            1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                    assertDv(d, "ext_not_null@Field_explicitlyFinal",
                            1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                    return;
                }
                // this.
                if (d.variable() instanceof This) {
                    assertEquals(TYPE + ".this", d.variableName());
                    assertTrue(d.variableInfo().isRead());
                    assertEquals("instance type Basics_0", d.currentValue().toString());
                    assertDvInitial(d, MultiLevel.NOT_INVOLVED_DV, EXTERNAL_NOT_NULL);
                    assertDv(d, MultiLevel.NOT_INVOLVED_DV, EXTERNAL_NOT_NULL);
                    return;
                }
                // the return value
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals((TYPE + ".getExplicitlyFinal()"), d.variableName());
                    String expectReturn = d.iteration() == 0 ? "<f:explicitlyFinal>" : "\"abc\"";
                    assertEquals(expectReturn, d.currentValue().toString());

                    String linked = "this.explicitlyFinal:0";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());

                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                    return;
                }
            }
            fail("Method name " + d.methodInfo().name + ", iteration " + d.iteration() + ", variable " + d.variableName() +
                    ", statement id " + d.statementId());
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Basics_0".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.CONTAINER_DV, CONTAINER);
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
            }
        };
        TypeMapVisitor typeMapVisitor = typeMap -> {
            // quick check that the XML annotations have been read properly, and copied into the correct place
            TypeInfo stringType = typeMap.getPrimitives().stringTypeInfo();
            assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV,
                    stringType.typeAnalysis.get().getProperty(Property.IMMUTABLE));
        };

        testClass("Basics_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(afterFieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

}
