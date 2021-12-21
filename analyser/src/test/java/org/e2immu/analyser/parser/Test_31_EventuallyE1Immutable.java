
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

import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_31_EventuallyE1Immutable extends CommonTestRunner {

    public Test_31_EventuallyE1Immutable() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.EventuallyE1Immutable_0";
        final String STRING = TYPE + ".string";
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("setString".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    assertTrue(d.haveMarkRead(STRING));
                    VariableInfoContainer stringVic = d.statementAnalysis().variables.get(STRING);
                    assertEquals(Level.FALSE_DV, stringVic.getPreviousOrInitial().getProperty(Property.CONTEXT_MODIFIED));
                    assertTrue(stringVic.hasEvaluation());
                    assertFalse(stringVic.hasMerge());
                    assertEquals("", stringVic.getPreviousOrInitial().getLinkedVariables().toString());
                    assertEquals("this.string:0", stringVic.current().getLinkedVariables().toString());
                    assertEquals(Level.FALSE_DV, stringVic.current().getProperty(Property.CONTEXT_MODIFIED));
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setString".equals(d.methodInfo().name) || "setString2".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId()) && d.variable() instanceof FieldReference fr && "string".equals(fr.fieldInfo.name)) {
                    assertNotEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setString".equals(d.methodInfo().name) || "setString2".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertNotNull(d.methodAnalysis().getPreconditionForEventual());
                    assertEquals("null==string",
                            d.methodAnalysis().getPreconditionForEventual().expression().toString());
                    if (d.iteration() > 3) {
                        MethodAnalysis.Eventual eventual = d.methodAnalysis().getEventual();
                        assertNotNull(eventual);
                        assertEquals("string", eventual.markLabel());
                    }
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyE1Immutable_0".equals(d.typeInfo().simpleName)) {
                int expectSize = d.iteration() == 0 ? 0 : 1;
                assertEquals(expectSize, d.typeAnalysis().getApprovedPreconditionsE1().size());

                assertDv(d, 1, MultiLevel.EVENTUALLY_E1IMMUTABLE_DV, Property.IMMUTABLE);

                String expectFields = d.iteration() == 0 ? "" : "string";
                assertEquals(expectFields, d.typeAnalysis().marksRequiredForImmutable().stream()
                        .map(f -> f.name).collect(Collectors.joining()));
                assertEquals(expectFields, d.typeAnalysis().markLabel());
                // direct conditions on fields, no fields of eventually immutable type
                assertTrue(d.typeAnalysis().getEventuallyImmutableFields().isEmpty());
            }
        };

        testClass("EventuallyE1Immutable_0", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("EventuallyE1Immutable_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_2() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyE1Immutable_2_M".equals(d.typeInfo().simpleName)) {
                String expectApprovedPreconditions = d.iteration() == 0 ? "{}" : "{j=j<=0}";
                assertEquals(expectApprovedPreconditions, d.typeAnalysis().getApprovedPreconditionsE1().toString());
                assertEquals(expectApprovedPreconditions, d.typeAnalysis().getApprovedPreconditionsE2().toString());
            }
        };

        testClass("EventuallyE1Immutable_2_M", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setString".equals(d.methodInfo().name) || "setString2".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertNotNull(d.methodAnalysis().getPreconditionForEventual());
                    assertEquals("null==string",
                            d.methodAnalysis().getPreconditionForEventual().expression().toString());
                    if (d.iteration() > 3) {
                        MethodAnalysis.Eventual eventual = d.methodAnalysis().getEventual();
                        assertNotNull(eventual);
                        assertEquals("string", eventual.markLabel());
                    }
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyE1Immutable_3".equals(d.typeInfo().simpleName)) {
                int expectSize = d.iteration() == 0 ? 0 : 1;
                assertEquals(expectSize, d.typeAnalysis().getApprovedPreconditionsE1().size());
                assertEquals(0, d.typeAnalysis().getApprovedPreconditionsE2().size());
                assertDv(d, 1, MultiLevel.EVENTUALLY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        testClass("EventuallyE1Immutable_3", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }
}
