
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
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.UnknownExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.basics.testexample.Basics_6;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintStream;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.model.MultiLevel.MUTABLE_DV;
import static org.junit.jupiter.api.Assertions.*;

public class Test_00_Basics_6 extends CommonTestRunner {
    public Test_00_Basics_6() {
        super(true);
    }

    // basic statement timing
    @Test
    public void test6() throws IOException {
        final String TYPE = "org.e2immu.analyser.parser.basics.testexample.Basics_6";
        final String FIELD = TYPE + ".field";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setField".equals(d.methodInfo().name) && d.variableInfo().variable() instanceof ParameterInfo) {
                assertSame(LinkedVariables.EMPTY, d.variableInfoContainer().getPreviousOrInitial().getLinkedVariables());
                assertEquals("this.field:0", d.variableInfo().getLinkedVariables().toString());
            }

            if ("test1".equals(d.methodInfo().name)) {
                if (FIELD.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("[1]", d.variableInfo().getReadAtStatementTimes().toString());
                    }
                }

                if ("v1".equals(d.variableName()) && "0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<f:field>" : "field$0";
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("v2".equals(d.variableName()) && "2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<f:field>" : "field$1";
                    assertEquals(expect, d.currentValue().toString());
                }
            }

            if ("test2".equals(d.methodInfo().name)) {
                if ("v1".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertDv(d, DV.FALSE_DV, CONTEXT_MODIFIED);
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("this.field:0,v2:0", d.variableInfo().getLinkedVariables().toString());
                        // evaluation to write linked properties
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("this.field:0,v2:0", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    }
                }
                if ("v2".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("this.field:0,v1:0", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    }
                }
                if (FIELD.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("v1:0,v2:0", d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("test3".equals(d.methodInfo().name)) {
                if ("v1".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:field>" : "field$0";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertEquals("this.field:0", d.variableInfo().getLinkedVariables().toString());
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:field>" : "field$0";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    }
                }
                if ("v3".equals(d.variableName())) {
                    String expectValue = d.iteration() == 0 ? "<m:someMinorMethod>" : "field$0.toUpperCase()";
                    assertEquals(expectValue, d.currentValue().toString());
                }

                if (FIELD.equals(d.variableName()) && "1".equals(d.statementId())) {
                    assertEquals(d.iteration() == 0, d.getProperty(CONTEXT_NOT_NULL).isDelayed());
                }
                if (FIELD.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    }
                }
                if (d.variable() instanceof This) {
                    assertDv(d, 2, MUTABLE_DV, EXTERNAL_IMMUTABLE);
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("4".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<s:String>" : "field$0.toUpperCase()";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                    }
                }
            }
            if ("test5".equals(d.methodInfo().name) && FIELD.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                }
                if ("1".equals(d.statementId())) {
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int timeI = d.statementAnalysis().statementTime(Stage.INITIAL);
            int timeE = d.statementAnalysis().statementTime(Stage.EVALUATION);
            int timeM = d.statementAnalysis().statementTime(Stage.MERGE);
            int numVariables = d.statementAnalysis().numberOfVariables();

            if ("test1".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(3, numVariables);
                        assertEquals(0, timeI);
                        assertEquals(0, timeE);
                        assertEquals(0, timeM);
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals(0, timeI);
                        assertEquals(1, timeE);
                        assertEquals(1, timeM);
                        // 4 vars: field, this, v1, out
                        assertEquals(4, numVariables);
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals(1, timeI);
                        assertEquals(1, timeE);
                        assertEquals(1, timeM);
                        // 5 vars: field, this, v1, v2, out
                        assertEquals(5, numVariables);
                    }
                    if ("3".equals(d.statementId())) {
                        assertEquals(1, timeI);
                        assertEquals(1, timeE);
                        assertEquals(1, timeM);
                        // 5 vars: field, this, v1, v2, out
                        assertEquals(5, numVariables);
                    }
                } else if (d.iteration() == 1) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(3, numVariables);
                    }
                }
                if ("3".equals(d.statementId())) {
                    assertNull(d.haveError(Message.Label.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                }
            }

            if ("test2".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId()) && d.iteration() > 0) {
                    // this, field, v1
                    assertEquals(3, numVariables);
                }
                if ("1".equals(d.statementId()) && d.iteration() > 0) {
                    // this, field, v1, v2
                    assertEquals(4, numVariables);
                    Expression valueV1 = d.statementAnalysis().getVariable("v1").current().getValue();
                    Expression valueV2 = d.statementAnalysis().getVariable("v2").current().getValue();
                    if (valueV1 instanceof VariableExpression v1r && valueV2 instanceof VariableExpression v2r) {
                        assertEquals(v1r.variable(), v2r.variable());
                    } else fail();
                }
                if ("2".equals(d.statementId()) && d.iteration() > 0) {
                    assertEquals("true", d.statementAnalysis().stateData().valueOfExpression.get().toString());
                    assertTrue(d.statementAnalysis().stateData().valueOfExpression.isFinal());
                    assertNotNull(d.haveError(Message.Label.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                }
            }

            if ("test4".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals(0, timeI);
                    assertEquals(1, timeE);
                    assertEquals(1, timeM);
                }
                if ("3".equals(d.statementId())) {
                    Message msg = d.haveError(Message.Label.ASSERT_EVALUATES_TO_CONSTANT_TRUE);
                    assertNull(msg);
                }
            }

            if ("test3".equals(d.methodInfo().name)) {
                assertEquals(0, timeI);
                assertEquals(0, timeE);
                assertEquals(0, timeM);

                if ("3".equals(d.statementId()) && d.iteration() > 0) {
                    assertNotNull(d.haveError(Message.Label.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                }
            }

            if ("test6".equals(d.methodInfo().name)) {
                if ("3".equals(d.statementId()) && d.iteration() > 0) {
                    assertNotNull(d.haveError(Message.Label.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo system = typeMap.get(System.class);
            FieldInfo out = system.getFieldByName("out", true);
            assertEquals(DV.TRUE_DV, out.fieldAnalysis.get().getProperty(FINAL));
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                    out.fieldAnalysis.get().getProperty(EXTERNAL_NOT_NULL));

            TypeInfo string = typeMap.get(String.class);
            MethodInfo equals = string.findUniqueMethod("equals", 1);
            assertEquals(DV.FALSE_DV, equals.methodAnalysis.get().getProperty(MODIFIED_METHOD));

            MethodInfo toLowerCase = string.findUniqueMethod("toLowerCase", 0);
            assertFalse(toLowerCase.methodResolution.get().allowsInterrupts());

            MethodInfo toUpperCase = string.findUniqueMethod("toUpperCase", 0);
            assertFalse(toLowerCase.methodResolution.get().allowsInterrupts());
            assertEquals(DV.FALSE_DV, toUpperCase.methodAnalysis.get().getProperty(MODIFIED_METHOD));

            TypeInfo printStream = typeMap.get(PrintStream.class);
            MethodInfo println = printStream.findUniqueMethod("println", 0);
            assertTrue(println.methodResolution.get().allowsInterrupts());
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("someMinorMethod".equals(d.methodInfo().name)) {
                assertEquals("/*inline someMinorMethod*/s.toUpperCase()", // no transfer of length, we have no info on s
                        d.methodAnalysis().getSingleReturnValue().toString());
                assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.methodAnalysis().getProperty(NOT_NULL_EXPRESSION));
                assertEquals("", d.methodInfo().methodResolution.get().methodsOfOwnClassReachedSorted());
                assertFalse(d.methodInfo().methodResolution.get().allowsInterrupts());

                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_PARAMETER);
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(MODIFIED_METHOD));
                assertDv(d.p(0), 1, DV.FALSE_DV, MODIFIED_VARIABLE);
            }
            if ("nonPrivateMethod".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().methodResolution.get().allowsInterrupts());
                assertTrue(d.methodAnalysis().getPrecondition().isEmpty());
            }
            if ("test3".equals(d.methodInfo().name)) {
                assertEquals("someMinorMethod", d.methodInfo().methodResolution.get().methodsOfOwnClassReachedSorted());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("field".equals(d.fieldInfo().name)) {
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(FINAL));
                assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
                assertTrue(d.fieldAnalysis().getValue() instanceof UnknownExpression);

                assertEquals(MultiLevel.NULLABLE_DV, d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test2".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                assertTrue(d.iteration() == 0 || d.evaluationResult().causesOfDelay().isDone(),
                        "Got " + d.evaluationResult().causesOfDelay());

                String expectValue = d.iteration() == 0 ? "<m:equals>" : "true";
                assertEquals(expectValue, d.evaluationResult().value().toString());
                EvaluationResult.ChangeData changeDataV1 = d.findValueChange("v1");
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, changeDataV1.getProperty(CONTEXT_NOT_NULL));

                assertEquals(d.iteration() >= 1, d.haveValueChange(FIELD));
            }
            if ("test3".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    EvaluationResult.ChangeData cdField = d.findValueChange("v1");
                    assertEquals("cnn@Parameter_s", cdField.getProperty(CONTEXT_NOT_NULL).causesOfDelay().toString());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Basics_6".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.MUTABLE_DV, IMMUTABLE);
                mustSeeIteration(d, 1);
            }
        };

        TypeContext typeContext = testClass("Basics_6", 0, 10, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
            //    .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
        TypeInfo b6 = typeContext.getFullyQualified(Basics_6.class);
        MethodInfo test5 = b6.findUniqueMethod("test5", 0);
        assertEquals(0, test5.methodAnalysis.get().getComputedCompanions().size());
    }
}
