
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

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.testexample.Basics_6;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class Test_00_Basics_4plus extends CommonTestRunner {
    public Test_00_Basics_4plus() {
        super(true);
    }

    // i = i + 1 on a field
    @Test
    public void test4() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Basics_4";
        final String I = TYPE + ".i";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("increment".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                if (I.equals(d.variableName())) {
                    String expect = d.iteration() == 0 ? "1+<f:i>" : "1+i$0";
                    assertEquals(expect, d.currentValue().toString());
                }
            }
        };
        testClass("Basics_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    // test here mainly to generate debug information for the output system
    @Test
    public void test5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo i && "input".equals(i.name)) {
                assertEquals(MultiLevel.DELAY, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name) && "0.0.0".equals(d.statementId())) {
                assertFalse(d.localConditionManager().isDelayed());
                assertEquals("null==s", d.condition().toString());
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo stream = typeMap.get(Stream.class);
            MethodInfo of = stream.findUniqueMethod("of", 1);
            assertFalse(of.methodInspection.get().isDefault());
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertFalse(d.methodInfo().isNotOverridingAnyOtherMethod());
            }
        };

        testClass("Basics_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    // basic statement timing
    @Test
    public void test6() throws IOException {

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("field".equals(d.fieldInfo().name)) {
                assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            }
        };
        final String TYPE = "org.e2immu.analyser.testexample.Basics_6";
        final String FIELD = TYPE + ".field";
        final String FIELD_0 = "field$0";
        final String FIELD_0_FQN = FIELD + "$0";
        final String FIELD_1_FQN = FIELD + "$1";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setField".equals(d.methodInfo().name) && d.variableInfo().variable() instanceof ParameterInfo) {
                assertSame(LinkedVariables.EMPTY, d.variableInfoContainer().getPreviousOrInitial().getLinkedVariables());
                assertEquals(LinkedVariables.EMPTY, d.variableInfo().getLinkedVariables());
            }

            if ("test1".equals(d.methodInfo().name)) {
                if (FIELD.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        int expect = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 0;
                        assertEquals(expect, d.variableInfo().getStatementTime());
                    }
                    if ("1".equals(d.statementId())) {
                        int expect = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 0;
                        assertEquals(expect, d.variableInfo().getStatementTime());
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("[1]", d.variableInfo().getReadAtStatementTimes().toString());
                        int expect = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 1;
                        assertEquals(expect, d.variableInfo().getStatementTime());
                    }
                }
                if (FIELD_0_FQN.equals(d.variableName())) {
                    assertTrue(d.iteration() > 0);
                    assertEquals("nullable instance type String", d.currentValue().toString());
                    assertEquals(VariableInfoContainer.NOT_A_VARIABLE_FIELD, d.variableInfo().getStatementTime());
                }
                if (FIELD_1_FQN.equals(d.variableName())) {
                    assertTrue(d.iteration() > 0);
                    assertEquals("nullable instance type String", d.currentValue().toString());
                    assertEquals(VariableInfoContainer.NOT_A_VARIABLE_FIELD, d.variableInfo().getStatementTime());
                    assertTrue("2".compareTo(d.statementId()) <= 0);
                }
                if ("v1".equals(d.variableName()) && "0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<f:field>" : FIELD_0;
                    assertEquals(expect, d.currentValue().toString());
                }
            }

            if ("test2".equals(d.methodInfo().name)) {
                if ("v1".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
                if (FIELD_0_FQN.equals(d.variableName())) {
                    assert d.iteration() > 0;
                    if ("0".equals(d.statementId())) {
                        assertEquals("this.field", d.variableInfo().getStaticallyAssignedVariables().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                }
            }
            if ("test3".equals(d.methodInfo().name)) {
                if ("v1".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("this.field", d.variableInfo().getStaticallyAssignedVariables().toString());
                        String expectValue = d.iteration() == 0 ? "<f:field>" : FIELD_0;
                        assertEquals(expectValue, d.currentValue().toString());
                        assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:field>" : FIELD_0;
                        assertEquals(expectValue, d.currentValue().toString());
                        int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(expectCnn, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                }
                if ("v3".equals(d.variableName())) {
                    String expectValue = d.iteration() == 0 ? "<m:someMinorMethod>" : "field$0.toUpperCase()";
                    assertEquals(expectValue, d.currentValue().toString());
                }

                if (FIELD_0_FQN.equals(d.variableName()) && "1".equals(d.statementId())) {
                    assertEquals(d.iteration() == 0, d.getProperty(VariableProperty.CONTEXT_NOT_NULL) == Level.DELAY);
                }
                if (FIELD.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                    if ("1".equals(d.statementId())) {
                        int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(expectCnn, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                    if ("2".equals(d.statementId())) {
                        int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(expectCnn, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                }

                if (d.variable() instanceof ReturnVariable) {
                    if ("4".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<m:someMinorMethod>" : "field$0.toUpperCase()";
                        assertEquals(expectValue, d.currentValue().toString());
                        int effectivelyNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(effectivelyNotNull, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                }
            }
            if ("test5".equals(d.methodInfo().name) && FIELD.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int timeI = d.statementAnalysis().statementTime(VariableInfoContainer.Level.INITIAL);
            int timeE = d.statementAnalysis().statementTime(VariableInfoContainer.Level.EVALUATION);
            int timeM = d.statementAnalysis().statementTime(VariableInfoContainer.Level.MERGE);

            if ("test1".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(3, d.statementAnalysis().variables.size());
                        assertEquals(0, timeI);
                        assertEquals(0, timeE);
                        assertEquals(0, timeM);
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals(0, timeI);
                        assertEquals(1, timeE);
                        assertEquals(1, timeM);
                        // 4 vars: field, this, v1, out
                        assertEquals(4, d.statementAnalysis().variables.size());
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals(1, timeI);
                        assertEquals(1, timeE);
                        assertEquals(1, timeM);
                        // 5 vars: field, this, v1, v2, out
                        assertEquals(5, d.statementAnalysis().variables.size());
                    }
                    if ("3".equals(d.statementId())) {
                        assertEquals(1, timeI);
                        assertEquals(1, timeE);
                        assertEquals(1, timeM);
                        // 5 vars: field, this, v1, v2, out
                        assertEquals(5, d.statementAnalysis().variables.size());
                    }
                } else if (d.iteration() == 1) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(4, d.statementAnalysis().variables.size());
                    }
                }
                if ("3".equals(d.statementId())) {
                    assertNull(d.haveError(Message.Label.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                }
            }

            if ("test2".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId()) && d.iteration() > 0) {
                    // this, field, field$0, v1
                    assertEquals(4, d.statementAnalysis().variables.size());
                }
                if ("1".equals(d.statementId()) && d.iteration() > 0) {
                    // this, field, field$0, v1, v2
                    assertEquals(5, d.statementAnalysis().variables.size());
                    Expression valueV1 = d.statementAnalysis().variables.get("v1").current().getValue();
                    Expression valueV2 = d.statementAnalysis().variables.get("v2").current().getValue();
                    assertTrue(valueV1 instanceof VariableExpression);
                    assertTrue(valueV2 instanceof VariableExpression);
                    Variable v1Redirected = ((VariableExpression) valueV1).variable();
                    Variable v2Redirected = ((VariableExpression) valueV2).variable();
                    assertEquals(v1Redirected, v2Redirected);
                }
                if ("2".equals(d.statementId()) && d.iteration() > 0) {
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
            assertEquals(Level.TRUE, out.fieldAnalysis.get().getProperty(VariableProperty.FINAL));
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                    out.fieldAnalysis.get().getProperty(VariableProperty.EXTERNAL_NOT_NULL));

            TypeInfo string = typeMap.get(String.class);
            MethodInfo equals = string.findUniqueMethod("equals", 1);
            assertEquals(Level.FALSE, equals.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));

            MethodInfo toLowerCase = string.findUniqueMethod("toLowerCase", 0);
            assertFalse(toLowerCase.methodResolution.get().allowsInterrupts());

            MethodInfo toUpperCase = string.findUniqueMethod("toUpperCase", 0);
            assertFalse(toLowerCase.methodResolution.get().allowsInterrupts());
            assertEquals(Level.FALSE, toUpperCase.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));

            TypeInfo printStream = typeMap.get(PrintStream.class);
            MethodInfo println = printStream.findUniqueMethod("println", 0);
            assertTrue(println.methodResolution.get().allowsInterrupts());
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("someMinorMethod".equals(d.methodInfo().name)) {
                assertEquals("s.toUpperCase()", // no transfer of length, we have no info on s
                        d.methodAnalysis().getSingleReturnValue().toString());
                assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));

                assertFalse(d.methodInfo().methodResolution.get().allowsInterrupts());
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                assertEquals(MultiLevel.DELAY, p0.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                int expectContextNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectContextNotNull, p0.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                int expectMv = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMv, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("nonPrivateMethod".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().methodResolution.get().allowsInterrupts());
                assertTrue(d.methodAnalysis().getPrecondition().isEmpty());
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test2".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<m:equals>" : "true";
                assertEquals(expectValue, d.evaluationResult().value().toString());
                EvaluationResult.ChangeData changeDataV1 = d.findValueChange("v1");
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, changeDataV1.getProperty(VariableProperty.CONTEXT_NOT_NULL));

                assertEquals(d.iteration() >= 1, d.haveValueChange(FIELD_0_FQN));
            }
            if ("test3".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    EvaluationResult.ChangeData cdField = d.findValueChange("v1");
                    assertEquals(Level.TRUE, cdField.getProperty(VariableProperty.CONTEXT_NOT_NULL_DELAY));
                } else {
                    EvaluationResult.ChangeData cdField0 = d.findValueChange(FIELD_0_FQN);
                    assertEquals(Level.DELAY, cdField0.getProperty(VariableProperty.CONTEXT_NOT_NULL_DELAY));
                    //    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, cdField0.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
            }
        };

        // IMPROVE: at least one potential null pointer too many a t m, field+v1 in test3; we should report only one
        // likely duplication because of inlining
        TypeContext typeContext = testClass("Basics_6", 0, 11, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
        TypeInfo b6 = typeContext.getFullyQualified(Basics_6.class);
        MethodInfo test5 = b6.findUniqueMethod("test5", 0);
        assertEquals(0, test5.methodAnalysis.get().getComputedCompanions().size());
    }


    // more on statement time
    @Test
    public void test7() throws IOException {
        final String I = "org.e2immu.analyser.testexample.Basics_7.i";
        final String I0 = "i$0";
        final String I1 = "i$1";
        final String I0_FQN = I + "$0";
        final String I1_FQN = I + "$1";
        final String I101_FQN = I + "$1$1_0_1-E";
        final String INC3_RETURN_VAR = "org.e2immu.analyser.testexample.Basics_7.increment3()";
        final String I_DELAYED = "<f:i>";
        final String INSTANCE_TYPE_INT_IDENTITY = "instance type int/*@Identity*/";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("increment".equals(d.methodInfo().name) && "3".equals(d.statementId())) {
                EvaluationResult.ChangeData cd = d.findValueChange(I);
                assertEquals(Level.DELAY, cd.getProperty(VariableProperty.CONTEXT_NOT_NULL_DELAY));
            }

            if ("increment".equals(d.methodInfo().name) && "4".equals(d.statementId()) && d.iteration() > 0) {
                assertEquals("true", d.evaluationResult().value().toString());
            }
            if ("increment3".equals(d.methodInfo().name) && "1.0.3".equals(d.statementId()) && d.iteration() > 0) {
                assertEquals("true", d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Basics_7".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo p && "p".equals(p.name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("0.0.0" + VariableInfoContainer.Level.EVALUATION, d.variableInfo().getReadId());
                    assertEquals(INSTANCE_TYPE_INT_IDENTITY, d.currentValue().toString());
                }
                if ("0.0.1".equals(d.statementId())) {
                    // READ IMPLICITLY via the variable 'i'
                    assertEquals("0.0.1" + VariableInfoContainer.Level.EVALUATION, d.variableInfo().getReadId());
                    String expectValue = d.iteration() == 0 ? "<p:p>" : INSTANCE_TYPE_INT_IDENTITY;
                    assertEquals(expectValue, d.currentValue().toString());

                    int expectIdentity = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectIdentity, d.getProperty(VariableProperty.IDENTITY));
                }
                if ("0".equals(d.statementId())) {
                    assertTrue(d.variableInfoContainer().hasMerge());
                    assertEquals("0" + VariableInfoContainer.Level.MERGE, d.variableInfo().getReadId());

                    String expectValue = d.iteration() == 0 ? "b?<p:p>:" + INSTANCE_TYPE_INT_IDENTITY : INSTANCE_TYPE_INT_IDENTITY;
                    assertEquals(expectValue, d.currentValue().toString());
                    int expectIdentity = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectIdentity, d.getProperty(VariableProperty.IDENTITY));
                }
            }

            if ("Basics_7".equals(d.methodInfo().name) && d.variable() instanceof FieldReference fr
                    && "out".equals(fr.fieldInfo.name)) {
                if ("0.0.1".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<f:out>" : "instance type PrintStream";
                    assertEquals(expectValue, d.currentValue().toString());
                    int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                }
                if ("1.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<f:out>" : "instance type PrintStream";
                    assertEquals(expectValue, d.currentValue().toString());
                    int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                }
            }

            if ("increment".equals(d.methodInfo().name) && I.equals(d.variableName())) {
                if ("2".equals(d.statementId()) && d.iteration() > 0) {
                    assertEquals(I0 + "+q", d.currentValue().toString());
                }
            }
            if ("increment3".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals(INC3_RETURN_VAR, d.variableName());
                    String expected = d.iteration() == 0 ? "-1+<f:i>==<f:i>" : "true";
                    if ("1.0.3".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("j".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? I_DELAYED : I1;
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if (I0_FQN.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("this.i", d.variableInfo().getStaticallyAssignedVariables().toString());

                        assertTrue(d.iteration() > 0); // does not exist earlier!
                        assertEquals("instance type int", d.currentValue().toString());
                    }
                }
                if (I1_FQN.equals(d.variableName())) {
                    // exists from 1.0.0 onwards
                    assertTrue(d.iteration() > 0); // does not exist earlier!
                    assertEquals("instance type int", d.currentValue().toString());
                    String expectStaticallyAssigned = d.statementId().equals("1.0.0") ? "this.i" : "";
                    // after the assignment, i becomes a different value
                    assertEquals(expectStaticallyAssigned, d.variableInfo().getStaticallyAssignedVariables().toString());
                }
                if (I101_FQN.equals(d.variableName())) {
                    assertEquals("this.i", d.variableInfo().getStaticallyAssignedVariables().toString());

                    // is primitive
                    if ("1.0.1".equals(d.statementId())) {
                        fail();
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        assertEquals("1+" + I1, d.currentValue().toString());
                        assertEquals("1.0.2-C", d.variableInfo().getReadId());
                    }
                    if ("1.0.3".equals(d.statementId())) {
                        assertEquals("1+" + I1, d.currentValue().toString());
                        assertEquals("1.0.2-C", d.variableInfo().getReadId());
                    }
                }
                if (I.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? I_DELAYED : "instance type int";
                        assertEquals(expect, d.currentValue().toString());
                        int expectStatementTime = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 0;
                        assertEquals(expectStatementTime, d.variableInfo().getStatementTime());
                        assertEquals("[0]", d.variableInfo().getReadAtStatementTimes().toString());
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? I_DELAYED : "instance type int";
                        assertEquals(expect, d.currentValue().toString());
                        int expectStatementTime = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 1;
                        assertEquals(expectStatementTime, d.variableInfo().getStatementTime());
                        assertEquals("[1]", d.variableInfo().getReadAtStatementTimes().toString());
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1+<f:i>" : "1+" + I1;
                        assertEquals(expect, d.currentValue().toString());
                        assertEquals("1.0.2-E", d.variableInfo().getReadId());
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1+<f:i>" : "1+" + I1;
                        assertEquals(expect, d.currentValue().toString());
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        assertEquals("", d.variableInfo().getStaticallyAssignedVariables().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int time1 = d.statementAnalysis().statementTime(VariableInfoContainer.Level.INITIAL);
            int time3 = d.statementAnalysis().statementTime(VariableInfoContainer.Level.EVALUATION);
            int time4 = d.statementAnalysis().statementTime(VariableInfoContainer.Level.MERGE);

            // method itself is synchronised, so statement time stands still
            if ("increment".equals(d.methodInfo().name)) {
                assertEquals(0, time1);
                assertEquals(0, time3);
                assertEquals(0, time4);
            }
            if ("increment2".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(0, time1);
                    assertEquals(1, time3);
                    assertEquals(1, time4);
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(1, time1);
                    assertEquals(1, time3);
                    assertEquals(1, time4);
                }
            }
            if ("increment3".equals(d.methodInfo().name)) {
                if (d.statementId().startsWith("1.0")) {
                    assertEquals("true", d.state().toString());
                    assertEquals("true", d.condition().toString());
                    assertEquals("true", d.absoluteState().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("increment".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isSynchronized());
            }
            if ("increment3".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "-1+<f:i>==<f:i>" : "true";
                assertEquals(expected, d.methodAnalysis().getLastStatement()
                        .variables.get(INC3_RETURN_VAR).current().getValue().toString());
                if (d.iteration() > 0) {
                    assertEquals("true", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
        };

        testClass("Basics_7", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    // assignment ids for local variables
    @Test
    public void test8() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Basics_8";
        final String I = TYPE + ".i";
        final String I0 = "i$0";
        final String I1 = "i$1";
        final String I2 = "i$2";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test1".equals(d.methodInfo().name)) {
                if ("v".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfo().isAssigned());
                        assertEquals("l", d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        assertEquals("3" + VariableInfoContainer.Level.EVALUATION,
                                d.variableInfo().getAssignmentIds().toString());
                        assertEquals("1+l", d.currentValue().toString());
                    }
                    if ("6".equals(d.statementId())) {
                        assertEquals("6" + VariableInfoContainer.Level.EVALUATION,
                                d.variableInfo().getAssignmentIds().toString());
                        assertEquals("2+l", d.currentValue().toString());
                    }
                }
                if ("w".equals(d.variableName()) && "1".equals(d.statementId())) {
                    assertEquals("1+l", d.currentValue().toString());
                }
                if ("u".equals(d.variableName()) && "4".equals(d.statementId())) {
                    assertEquals("3+l", d.currentValue().toString());
                }
            }

            if ("test3".equals(d.methodInfo().name) && d.iteration() > 0) {
                if ("j".equals(d.variableName()) && "0".equals(d.statementId())) {
                    assertEquals(I0, d.currentValue().toString());
                }
                if (I.equals(d.variableName()) && "1".equals(d.statementId())) {
                    assertEquals(I0 + "+q", d.currentValue().toString());
                }
                if ("k".equals(d.variableName()) && "2".equals(d.statementId())) {
                    assertEquals(I0 + "+q", d.currentValue().toString());
                }
            }
            if ("test4".equals(d.methodInfo().name)) {
                if ("j".equals(d.variableName())) {
                    String staticallyAssigned = d.variableInfo().getStaticallyAssignedVariables().toString();
                    if ("0".equals(d.statementId()) || "2".equals(d.statementId())
                            || "3".equals(d.statementId()) || "4.0.0.0.0".equals(d.statementId())
                            || "4".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:i>" : I1;
                        assertEquals(expectValue, d.currentValue().toString());
                        assertEquals("this.i", staticallyAssigned);
                    }
                    if ("4.0.0.0.1".equals(d.statementId()) || "4.0.0.0.2".equals(d.statementId()) ||
                            "4.0.0.0.3".equals(d.statementId()) || "4.0.0.0.4".equals(d.statementId()) ||
                            "4.0.0".equals(d.statementId())) {
                        assertEquals("", staticallyAssigned, "At " + d.statementId());
                    }
                }
                if ("k".equals(d.variableName())) {
                    if ("3".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:i>" : I2;
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
                if (d.iteration() > 0) {
                    if ("j0".equals(d.variableName()) && "4.0.0.0.0".equals(d.statementId())) {
                        assertEquals(I2, d.currentValue().toString());
                    }
                    if (I.equals(d.variableName()) && "4.0.0.0.1".equals(d.statementId())) {
                        assertEquals(I2 + "+q", d.currentValue().toString());
                    }
                    if ("k0".equals(d.variableName()) && "4.0.0.0.2".equals(d.statementId())) {
                        assertEquals(I2 + "+q", d.currentValue().toString());
                    }
                }
                if ("java.lang.System.out".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:out>" : "instance type PrintStream";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("4".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:out>" : "instance type PrintStream";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test4".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<m:println>" : "<no return value>";
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
            if ("test4".equals(d.methodInfo().name) && d.iteration() > 0 && "4.0.0.0.3".equals(d.statementId())) {
                assertEquals("true", d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("4.0.0.0.0".equals(d.statementId()) && d.iteration() > 0) {
                assertEquals(I1 + "==" + I2, d.absoluteState().toString());
            }
        };

        testClass("Basics_8", 0, 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }
}
