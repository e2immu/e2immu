
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

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

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
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "p0";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if (FIELD1.equals(d.variableName())) {
                if ("1".equals(d.statementId())) {
                    assertTrue(d.variableInfo().isAssigned());
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "p0";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());

                    assertEquals(MultiLevel.MUTABLE, d.getProperty(VariableProperty.CONTEXT_IMMUTABLE));
                }
            }
            if (d.variable() instanceof ParameterInfo p0 && "p0".equals(p0.name)) {
                String expectValue = "nullable instance type Set<String>/*@Identity*/";
                assertEquals(expectValue, d.currentValue().toString());
                assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                assertEquals(MultiLevel.MUTABLE, d.getProperty(VariableProperty.CONTEXT_IMMUTABLE));

                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }

            if (d.variable() instanceof This) {
                if ("0".equals(d.statementId())) {
                    assertTrue(d.variableInfoContainer().isInitial());
                    assertTrue(d.variableInfoContainer().hasEvaluation());
                    assertFalse(d.variableInfoContainer().hasMerge());
                    int expectImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE;
                    assertEquals(expectImm, d.getProperty(VariableProperty.IMMUTABLE));
                    assertEquals(expectImm, d.variableInfoContainer().getPreviousOrInitial().getProperty(VariableProperty.IMMUTABLE));
                }
                if ("1".equals(d.statementId())) {
                    int expectImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE;
                    assertEquals(expectImm, d.variableInfoContainer().getPreviousOrInitial().getProperty(VariableProperty.IMMUTABLE));
                }
            }
        }

        if ("getF1".equals(d.methodInfo().name)) {
            if (FIELD1.equals(d.variableName())) {
                assertTrue(d.variableInfo().isRead());
                if (d.iteration() == 0) {
                    assertTrue(d.variableInfo().getLinkedVariables().isDelayed());
                } else {
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                String expectValue = d.iteration() == 0 ? "<f:f1>" : "nullable instance type Set<String>";
                assertEquals(expectValue, d.currentValue().toString());
                int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectNN, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));

                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                int expectExtImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.MUTABLE;
                assertEquals(expectExtImm, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
            }
            if (GET_F1_RETURN.equals(d.variableName())) {
                assertTrue(d.variableInfo().isAssigned());
                if (d.iteration() == 0) {
                    assertTrue(d.variableInfo().getLinkedVariables().isDelayed());
                } else {
                    assertEquals("this.f1", d.variableInfo().getLinkedVariables().toString()); // without p0
                }
                String expectValue = d.iteration() == 0 ? "<f:f1>" : "f1";
                assertEquals(expectValue, d.currentValue().toString());
                int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectNN, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if (BASICS_1.equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            assertEquals(d.iteration() > 0, d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
        }
        if ("getF1".equals(d.methodInfo().name) && d.iteration() > 1) {
            assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("f1".equals(d.fieldInfo().name)) {
            assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            if (d.iteration() > 0) {
                assertEquals("p0", d.fieldAnalysis().getEffectivelyFinalValue().debugOutput());
                assertEquals("p0", d.fieldAnalysis().getLinkedVariables().toString());
            }
            int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
            assertEquals(expectModified, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));

            assertEquals(MultiLevel.MUTABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if (BASICS_1.equals(d.methodInfo().name)) {
            ParameterAnalysis p0 = d.parameterAnalyses().get(0);
            int expectContextModified = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
            assertEquals(expectContextModified, p0.getProperty(VariableProperty.CONTEXT_MODIFIED));
            int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
            assertEquals(expectModified, p0.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            assertEquals(expectModified, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));

            int expectContextNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            assertEquals(expectContextNotNull, p0.getProperty(VariableProperty.CONTEXT_NOT_NULL));
            int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            assertEquals(expectNN, p0.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            assertEquals(expectNN, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        }
        if ("getF1".equals(d.methodInfo().name)) {
            int expectMod = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
            assertEquals(expectMod, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            assertEquals(expectNN, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if ("Basics_1".equals(d.typeInfo().simpleName)) {
            assertTrue(d.typeAnalysis().getTransparentTypes().isEmpty());
            int expectImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE;
            assertEquals(expectImm, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo set = typeMap.get(Set.class);
        assertEquals(MultiLevel.MUTABLE, set.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
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
