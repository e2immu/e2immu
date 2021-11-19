
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
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.e2immu.analyser.analyser.Property.*;
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

                    assertDv(d, 0, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                }
            }
            if (FIELD1.equals(d.variableName())) {
                if ("1".equals(d.statementId())) {
                    assertTrue(d.variableInfo().isAssigned());
                    assertEquals("p0:0,s1:0,this.f1:0", d.variableInfo().getLinkedVariables().toString());

                    assertEquals(MultiLevel.MUTABLE_DV, d.getProperty(CONTEXT_IMMUTABLE));
                }
            }
            if (d.variable() instanceof ParameterInfo p0 && "p0".equals(p0.name)) {
                String expectValue = "nullable instance type Set<String>/*@Identity*/";
                assertEquals(expectValue, d.currentValue().toString());
                assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                assertEquals(MultiLevel.MUTABLE_DV, d.getProperty(CONTEXT_IMMUTABLE));
                assertDv(d, 0, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                assertDv(d, 0, MultiLevel.MUTABLE_DV, EXTERNAL_IMMUTABLE);
            }

            // unused parameter in the constructor
            if (d.variable() instanceof ParameterInfo p1 && "p1".equals(p1.name)) {
                String expectValue = "nullable instance type Set<String>";
                assertEquals(expectValue, d.currentValue().toString());
                assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                assertEquals(MultiLevel.MUTABLE_DV, d.getProperty(CONTEXT_IMMUTABLE));

                assertDv(d, 0, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                assertDv(d, 0, MultiLevel.NOT_INVOLVED_DV, EXTERNAL_IMMUTABLE);

            }

            if (d.variable() instanceof This) {
                if ("0".equals(d.statementId())) {
                    assertTrue(d.variableInfoContainer().isInitial());
                    assertTrue(d.variableInfoContainer().hasEvaluation());
                    assertFalse(d.variableInfoContainer().hasMerge());

                    String expectValue = "instance type Basics_1";
                    assertEquals(expectValue, d.currentValue().toString());

                    assertEquals(MultiLevel.MUTABLE_DV, d.getProperty(CONTEXT_IMMUTABLE));
                    assertEquals(MultiLevel.MUTABLE_DV, d.getProperty(IMMUTABLE));
                    assertDv(d, 0, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, EXTERNAL_IMMUTABLE);

                    assertEquals("this:0", d.variableInfo().getLinkedVariables().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, IMMUTABLE);
                }
            }
        }

        if ("getF1".equals(d.methodInfo().name)) {
            if (FIELD1.equals(d.variableName())) {
                assertTrue(d.variableInfo().isRead());

                assertEquals("return getF1:0,this.f1:0", d.variableInfo().getLinkedVariables().toString());

                String expectValue = d.iteration() == 0 ? "<f:f1>" : "nullable instance type Set<String>";
                assertEquals(expectValue, d.currentValue().toString());
                assertDv(d, 0, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
                assertDv(d, 0, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                assertDv(d, 0, MultiLevel.MUTABLE_DV, EXTERNAL_IMMUTABLE);
            }
            if (GET_F1_RETURN.equals(d.variableName())) {
                assertTrue(d.variableInfo().isAssigned());

                assertEquals("return getF1:0,this.f1:0", d.variableInfo().getLinkedVariables().toString()); // without p0

                String expectValue = d.iteration() == 0 ? "<f:f1>" : "f1";
                assertEquals(expectValue, d.currentValue().toString());
                assertDv(d, 0, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if (BASICS_1.equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            // there's nothing that generates a delay on linked variables
            assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished());
        }
        if ("getF1".equals(d.methodInfo().name)) {
            assertEquals(d.iteration() > 0, d.statementAnalysis().methodLevelData.linksHaveBeenEstablished());
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("f1".equals(d.fieldInfo().name)) {
            assertEquals(Level.TRUE_DV, d.fieldAnalysis().getProperty(FINAL));
            if (d.iteration() > 0) {
                assertEquals("p0", d.fieldAnalysis().getValue().debugOutput());
                assertEquals("p0:0", d.fieldAnalysis().getLinkedVariables().toString());
            }
            assertEquals(Level.FALSE_DV, d.fieldAnalysis().getProperty(MODIFIED_OUTSIDE_METHOD));
            assertEquals(MultiLevel.NULLABLE_DV, d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));
            assertEquals(MultiLevel.MUTABLE_DV, d.fieldAnalysis().getProperty(EXTERNAL_IMMUTABLE));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if (BASICS_1.equals(d.methodInfo().name)) {
            assertDv(d.p(0), 0, Level.FALSE_DV, CONTEXT_MODIFIED);
            assertDv(d.p(0), 0, Level.FALSE_DV, MODIFIED_OUTSIDE_METHOD);
            assertDv(d.p(0), 0, Level.FALSE_DV, MODIFIED_VARIABLE);

            assertDv(d.p(0), 0, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
            assertDv(d.p(0), 0, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
            assertDv(d.p(0), 0, MultiLevel.NULLABLE_DV, NOT_NULL_PARAMETER);
        }
        if ("getF1".equals(d.methodInfo().name)) {
            assertEquals(Level.FALSE_DV, d.methodAnalysis().getProperty(MODIFIED_METHOD));
            assertDv(d, 0, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if ("Basics_1".equals(d.typeInfo().simpleName)) {
            assertTrue(d.typeAnalysis().getTransparentTypes().isEmpty());
            assertDv(d, 0, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, IMMUTABLE);
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo set = typeMap.get(Set.class);
        assertEquals(MultiLevel.MUTABLE_DV, set.typeAnalysis.get().getProperty(IMMUTABLE));
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
