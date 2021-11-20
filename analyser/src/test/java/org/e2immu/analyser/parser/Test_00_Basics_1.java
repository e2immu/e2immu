
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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.model.Level.FALSE_DV;
import static org.e2immu.analyser.model.Level.TRUE_DV;
import static org.e2immu.analyser.model.MultiLevel.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_00_Basics_1 extends CommonTestRunner {

    public static final String BASICS_1 = "Basics_1";
    private static final String TYPE = "org.e2immu.analyser.testexample." + BASICS_1;
    private static final String FIELD1 = TYPE + ".f1";
    private static final String GET_F1_RETURN = TYPE + ".getF1()";

    public Test_00_Basics_1() {
        super(true);
    }

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if (BASICS_1.equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                assertEquals("p0", d.evaluationResult().getExpression().toString());
            }
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if (BASICS_1.equals(d.methodInfo().name)) {
            if ("s1".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertEquals("p0", d.currentValue().toString());
                    assertEquals("p0:0,s1:0", d.variableInfo().getLinkedVariables().toString());

                    assertDv(d, 1, NULLABLE_DV, EXTERNAL_NOT_NULL);
                }
            }
            if (d.variable() instanceof FieldReference fr && "f1".equals(fr.fieldInfo.name)) {
                assertEquals(FIELD1, d.variableName());
                if ("1".equals(d.statementId())) {
                    assertTrue(d.variableInfo().isAssigned());
                    assertEquals("p0:0,s1:0,this.f1:0", d.variableInfo().getLinkedVariables().toString());

                    assertEquals(MUTABLE_DV, d.getProperty(CONTEXT_IMMUTABLE));

                    assertCurrentValue(d, 0, "", "p0");
                }
            }
            if (d.variable() instanceof ParameterInfo p0 && "p0".equals(p0.name)) {
                String expectValue = "nullable instance type Set<String>/*@Identity*/";
                assertEquals(expectValue, d.currentValue().toString());
                assertEquals(NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                assertEquals(MUTABLE_DV, d.getProperty(CONTEXT_IMMUTABLE));
                assertDv(d, 1, NULLABLE_DV, EXTERNAL_NOT_NULL);
                assertDv(d, 1, MUTABLE_DV, EXTERNAL_IMMUTABLE);
            }

            /*
            unused parameter in the constructor
            the detection of unused happens in the 2nd iteration
            valid for statements 0 and 1
            */
            if (d.variable() instanceof ParameterInfo p1 && "p1".equals(p1.name)) {
                String expectValue = "nullable instance type Set<String>";
                assertEquals(expectValue, d.currentValue().toString());
                assertEquals(NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                assertEquals(MUTABLE_DV, d.getProperty(CONTEXT_IMMUTABLE));

                assertDvInitial(d, "ext_nn@Parameter_p1", 1, NOT_INVOLVED_DV, EXTERNAL_NOT_NULL);
                assertDvInitial(d, "ext_imm@Parameter_p1", 1, NOT_INVOLVED_DV, EXTERNAL_IMMUTABLE);
                assertFalse(d.variableInfoContainer().hasEvaluation());
                assertFalse(d.variableInfoContainer().hasMerge());
            }

            if (d.variable() instanceof This) {
                if ("0".equals(d.statementId())) {
                    assertTrue(d.variableInfoContainer().isInitial());
                    assertFalse(d.variableInfoContainer().hasEvaluation());
                    assertFalse(d.variableInfoContainer().hasMerge());

                    String expectValue = "instance type Basics_1";
                    assertEquals(expectValue, d.currentValue().toString());

                    assertEquals(MUTABLE_DV, d.getProperty(CONTEXT_IMMUTABLE));
                    assertEquals(MUTABLE_DV, d.getProperty(IMMUTABLE));
                    assertDv(d, 2, EFFECTIVELY_E1IMMUTABLE_DV, EXTERNAL_IMMUTABLE);
                    assertTrue(d.iteration() < 2);
                    assertEquals("this:0", d.variableInfo().getLinkedVariables().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(MUTABLE_DV, d.getProperty(IMMUTABLE));
                    assertDvInitial(d, "immutable@Class_Basics_1", 2, EFFECTIVELY_E1IMMUTABLE_DV, EXTERNAL_IMMUTABLE);
                    assertDv(d, 2, EFFECTIVELY_E1IMMUTABLE_DV, EXTERNAL_IMMUTABLE);
                    mustSeeIteration(d, 2);
                }
            }
        }

        if ("getF1".equals(d.methodInfo().name)) {
            if (d.variable() instanceof FieldReference fr && "f1".equals(fr.fieldInfo.name)) {
                assertEquals(FIELD1, d.variableName());
                assertTrue(d.variableInfo().isRead());

                assertCurrentValue(d, 1, "initial@Field_f1", "nullable instance type Set<String>");
                assertLinked(d, 0, "", "return getF1:0,this.f1:0");
                assertTrue(d.variableInfo().getLinkedVariables().isDone());

                assertDv(d, 1, NULLABLE_DV, NOT_NULL_EXPRESSION);
                assertDv(d, 1, NULLABLE_DV, EXTERNAL_NOT_NULL);
                assertDv(d, 1, MUTABLE_DV, EXTERNAL_IMMUTABLE);
            }
            if (GET_F1_RETURN.equals(d.variableName())) {
                assertTrue(d.variableInfo().isAssigned());

                assertEquals("return getF1:0,this.f1:0", d.variableInfo().getLinkedVariables().toString()); // without p0

                String expectValue = d.iteration() == 0 ? "<f:f1>" : "f1";
                assertEquals(expectValue, d.currentValue().toString());
                assertDv(d, 1, NULLABLE_DV, NOT_NULL_EXPRESSION);
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if (BASICS_1.equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            // there's nothing that generates a delay on linked variables
            assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished());
        }
        if ("getF1".equals(d.methodInfo().name)) {
            assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished());
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("f1".equals(d.fieldInfo().name)) {
            assertEquals(TRUE_DV, d.fieldAnalysis().getProperty(FINAL));

            assertEquals("p0", d.fieldAnalysis().getValue().debugOutput());
            assertEquals("p0:0", d.fieldAnalysis().getLinkedVariables().toString());

            assertEquals(FALSE_DV, d.fieldAnalysis().getProperty(MODIFIED_OUTSIDE_METHOD));
            assertEquals(NULLABLE_DV, d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));
            assertEquals(MUTABLE_DV, d.fieldAnalysis().getProperty(EXTERNAL_IMMUTABLE));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if (BASICS_1.equals(d.methodInfo().name)) {
            assertDv(d.p(0), 1, FALSE_DV, CONTEXT_MODIFIED);
            assertDv(d.p(0), 1, FALSE_DV, MODIFIED_OUTSIDE_METHOD);
            assertDv(d.p(0), 1, FALSE_DV, MODIFIED_VARIABLE);

            assertDv(d.p(0), 1, NULLABLE_DV, CONTEXT_NOT_NULL);
            assertDv(d.p(0), 1, NULLABLE_DV, EXTERNAL_NOT_NULL);
            assertDv(d.p(0), 1, NULLABLE_DV, NOT_NULL_PARAMETER);
        }
        if ("getF1".equals(d.methodInfo().name)) {
            assertEquals(FALSE_DV, d.methodAnalysis().getProperty(MODIFIED_METHOD));
            assertDv(d, 1, NULLABLE_DV, NOT_NULL_EXPRESSION);
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if ("Basics_1".equals(d.typeInfo().simpleName)) {
            assertTrue(d.typeAnalysis().getTransparentTypes().isEmpty());
            assertDv(d, 1, EFFECTIVELY_E1IMMUTABLE_DV, IMMUTABLE);
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo set = typeMap.get(Set.class);
        assertEquals(MUTABLE_DV, set.typeAnalysis.get().getProperty(IMMUTABLE));
    };

    @Test
    public void test() throws IOException {
        // two warnings: two unused parameters
        testClass(BASICS_1, 0, 2, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

}
