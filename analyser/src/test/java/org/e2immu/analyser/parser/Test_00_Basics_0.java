
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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.FieldAnalysis;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_00_Basics_0 extends CommonTestRunner {
    private static final String TYPE = "org.e2immu.analyser.testexample.Basics_0";

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

                assertEquals(d.iteration() == 0, d.evaluationResult().someValueWasDelayed());
            }
        };

        // everything in first iteration
        FieldAnalyserVisitor afterFieldAnalyserVisitor = d -> {
            FieldAnalysis fieldAnalysis = d.fieldAnalysis();
            if ("explicitlyFinal".equals(d.fieldInfo().name)) {
                assertEquals("", fieldAnalysis.getLinkedVariables().toString());
                assertEquals(Level.TRUE, fieldAnalysis.getProperty(VariableProperty.FINAL));
                assertEquals("\"abc\"", fieldAnalysis.getEffectivelyFinalValue().toString());
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(fieldAnalysis.getEffectivelyFinalValue(),
                        VariableProperty.NOT_NULL_EXPRESSION));
                assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE,
                        fieldAnalysis.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
                assertTrue(fieldAnalysis.getLinkedVariables().isEmpty());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if (d.methodInfo().name.equals("getExplicitlyFinal") && "0".equals(d.statementId())) {
                assertTrue(d.condition().isBoolValueTrue());
                assertTrue(d.state().isBoolValueTrue());
                assertTrue(d.localConditionManager().precondition().isEmpty());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.methodInfo().name.equals("getExplicitlyFinal") && "0".equals(d.statementId())) {
                // the field
                if ((TYPE + ".explicitlyFinal").equals(d.variableName())) {
                    assertFalse(d.variableInfo().isAssigned());
                    assertTrue(d.variableInfo().isRead());
                    if (d.iteration() == 0) {
                        assertTrue(d.currentValueIsDelayed());
                    } else {
                        assertEquals(new StringConstant(d.evaluationContext().getPrimitives(), "abc"), d.currentValue());
                    }
                    int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    return;
                }
                // this.
                if ((TYPE + ".this").equals(d.variableName())) {
                    assertTrue(d.variableInfo().isRead());
                    assertEquals("instance type Basics_0", d.currentValue().toString());
                    assertEquals(MultiLevel.NOT_INVOLVED, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    return;
                }
                // the return value
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals((TYPE + ".getExplicitlyFinal()"), d.variableName());
                    String expectReturn = d.iteration() == 0 ? "<f:explicitlyFinal>" : "\"abc\"";
                    assertEquals(expectReturn, d.currentValue().toString());

                    // self ref only
                    String expected = d.iteration() == 0 ? "this.explicitlyFinal:0,return getExplicitlyFinal:0" : "return getExplicitlyFinal:0";
                    assertEquals(expected, d.variableInfo().getLinkedVariables().toString());

                    int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                    assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    return;
                }
            }
            fail("Method name " + d.methodInfo().name + ", iteration " + d.iteration() + ", variable " + d.variableName() +
                    ", statement id " + d.statementId());
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            // quick check that the XML annotations have been read properly, and copied into the correct place
            TypeInfo stringType = typeMap.getPrimitives().stringTypeInfo;
            assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE,
                    stringType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        };

        testClass("Basics_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(afterFieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

}
