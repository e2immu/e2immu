
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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.UnknownExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.testexample.Basics_6;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintStream;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;
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
                    assertEquals("this.i:0", d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("getI".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expect = d.iteration() == 0 ? "<f:i>" : "i$0";
                    assertEquals(expect, d.currentValue().toString());
                    String expectLv = d.iteration() == 0 ? "return getI:0,this.i:0" : "i$0:1,return getI:0,this.i:0";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
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
                assertDv(d, 0, MultiLevel.NOT_INVOLVED_DV, EXTERNAL_NOT_NULL);
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
        final String TYPE = "org.e2immu.analyser.testexample.Basics_6";
        final String FIELD = TYPE + ".field";
        final String FIELD_0 = "field$0";
        final String FIELD_0_FQN = FIELD + "$0";
        final String FIELD_1_FQN = FIELD + "$1";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setField".equals(d.methodInfo().name) && d.variableInfo().variable() instanceof ParameterInfo) {
                assertSame(LinkedVariables.EMPTY, d.variableInfoContainer().getPreviousOrInitial().getLinkedVariables());
                assertEquals("field:0,this.field:0", d.variableInfo().getLinkedVariables().toString());
            }

            if ("test1".equals(d.methodInfo().name)) {
                if (FIELD.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        int expect = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 0;
                        assertEquals(expect, d.variableInfo().getStatementTime());
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
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
                        assertEquals(DV.FALSE_DV, d.getProperty(CONTEXT_MODIFIED));
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                    }
                    if ("1".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "this.field:0,v1:0,v2:0" // FIXME v2:0?
                                : "field$0:1,this.field:0,v1:0,v2:1";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        // evaluation to write linked properties
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                    }
                    if ("2".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "this.field:0,v1:0,v2:0"
                                : "field$0:1,this.field:0,v1:0,v2:1";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                }
                if ("v2".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "this.field:0,v1:0,v2:0"
                                : "field$0:1,this.field:0,v1:1,v2:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                }
                if (FIELD.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        int expect = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 0;
                        assertEquals(expect, d.variableInfo().getStatementTime());
                        assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
                    }
                    if ("1".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "this.field:0,v1:0,v2:0"
                                : "field$0:1,this.field:0,v1:0,v2:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (FIELD_1_FQN.equals(d.variableName())) {
                    fail(); // statement time has not advanced!
                }
                if (FIELD_0_FQN.equals(d.variableName())) {
                    assertTrue(d.iteration() > 0);
                    assertEquals("nullable instance type String", d.currentValue().toString(),
                            "Caused by delay? " + d.currentValue().causesOfDelay());
                    assertEquals(VariableInfoContainer.NOT_A_VARIABLE_FIELD, d.variableInfo().getStatementTime());
                    assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);

                    if ("0".equals(d.statementId())) {
                        assertEquals("field$0:0,this.field:0,v1:1",
                                d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                        assertEquals("field$0:0,this.field:0,v1:1,v2:1",
                                d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                }
            }
            if ("test3".equals(d.methodInfo().name)) {
                if ("v1".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:field>" : FIELD_0;
                        assertEquals(expectValue, d.currentValue().toString());
                        String expectedLv = d.iteration() == 0 ? "this.field:0,v1:0" : "field$0:1,this.field:0,v1:0";
                        assertEquals(expectedLv, d.variableInfo().getLinkedVariables().toString());
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:field>" : FIELD_0;
                        assertEquals(expectValue, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    }
                }
                if ("v3".equals(d.variableName())) {
                    String expectValue = d.iteration() == 0 ? "<m:someMinorMethod>" : "field$0.toUpperCase()";
                    assertEquals(expectValue, d.currentValue().toString());
                }

                if (FIELD_0_FQN.equals(d.variableName()) && "1".equals(d.statementId())) {
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
                    assertDvInitial(d, "immutable@Class_Basics_6", 1, MultiLevel.MUTABLE_DV, EXTERNAL_IMMUTABLE);
                    assertDv(d, 1, MultiLevel.MUTABLE_DV, EXTERNAL_IMMUTABLE);
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("4".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<m:someMinorMethod>" : "field$0.toUpperCase()";
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
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int timeI = d.statementAnalysis().statementTime(VariableInfoContainer.Level.INITIAL);
            int timeE = d.statementAnalysis().statementTime(VariableInfoContainer.Level.EVALUATION);
            int timeM = d.statementAnalysis().statementTime(VariableInfoContainer.Level.MERGE);
            int numVariables = ((StatementAnalysisImpl)d.statementAnalysis()).variables.size();

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
                        assertEquals(4, numVariables);
                    }
                }
                if ("3".equals(d.statementId())) {
                    assertNull(d.haveError(Message.Label.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                }
            }

            if ("test2".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId()) && d.iteration() > 0) {
                    // this, field, field$0, v1
                    assertEquals(4, numVariables);
                }
                if ("1".equals(d.statementId()) && d.iteration() > 0) {
                    // this, field, field$0, v1, v2
                    assertEquals(5, numVariables);
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
                assertEquals("s.toUpperCase()", // no transfer of length, we have no info on s
                        d.methodAnalysis().getSingleReturnValue().toString());
                assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.methodAnalysis().getProperty(NOT_NULL_EXPRESSION));
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
                assertTrue(d.iteration() == 0 || d.evaluationResult().causes().isDone(),
                        "Got " + d.evaluationResult().causes());

                String expectValue = d.iteration() == 0 ? "<m:equals>" : "true";
                assertEquals(expectValue, d.evaluationResult().value().toString());
                EvaluationResult.ChangeData changeDataV1 = d.findValueChange("v1");
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, changeDataV1.getProperty(CONTEXT_NOT_NULL));

                assertEquals(d.iteration() >= 1, d.haveValueChange(FIELD_0_FQN));
            }
            if ("test3".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    EvaluationResult.ChangeData cdField = d.findValueChange("v1");
                    assertEquals("cnn@Parameter_s", cdField.getProperty(CONTEXT_NOT_NULL).causesOfDelay().toString());
                }
            }
        };

        TypeContext typeContext = testClass("Basics_6", 0, 10, new DebugConfiguration.Builder()
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
        final String I101_FQN = I + "$1$1.0.1-E";
        final String INC3_RETURN_VAR = "org.e2immu.analyser.testexample.Basics_7.increment3()";
        final String I_DELAYED = "<f:i>";
        final String INSTANCE_TYPE_INT_IDENTITY = "instance type int/*@Identity*/";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("increment".equals(d.methodInfo().name) && "3".equals(d.statementId())) {
                EvaluationResult.ChangeData cd = d.findValueChange(I);
                assertFalse(cd.getProperty(CONTEXT_NOT_NULL).isDelayed());
            }

            if ("increment".equals(d.methodInfo().name) && "4".equals(d.statementId()) && d.iteration() > 0) {
                assertEquals("true", d.evaluationResult().value().toString());
            }
            if ("increment3".equals(d.methodInfo().name) && "1.0.3".equals(d.statementId()) && d.iteration() > 0) {
                assertEquals("true", d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Basics_7".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "p".equals(p.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("0.0.0" + VariableInfoContainer.Level.EVALUATION, d.variableInfo().getReadId());
                        assertEquals(INSTANCE_TYPE_INT_IDENTITY, d.currentValue().toString());
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        // READ IMPLICITLY via the variable 'i'
                        assertEquals("0.0.1" + VariableInfoContainer.Level.EVALUATION, d.variableInfo().getReadId());
                        assertEquals(INSTANCE_TYPE_INT_IDENTITY, d.currentValue().toString());
                        assertDv(d, 0, DV.TRUE_DV, IDENTITY);
                    }
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasMerge());
                        assertEquals("0" + VariableInfoContainer.Level.MERGE, d.variableInfo().getReadId());

                        assertEquals(INSTANCE_TYPE_INT_IDENTITY, d.currentValue().toString());
                        assertDv(d, 0, DV.TRUE_DV, IDENTITY);
                    }

                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL),
                            "in statement " + d.statementId());

                }
                if (d.variable() instanceof This) {
                    assertDv(d, 1, MultiLevel.NOT_INVOLVED_DV, EXTERNAL_IMMUTABLE);
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                }
                if (d.variable() instanceof ParameterInfo pi && "b".equals(pi.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                    assertDv(d, 1, MultiLevel.NOT_INVOLVED_DV, EXTERNAL_IMMUTABLE);
                }

                if (d.variable() instanceof FieldReference fr && "out".equals(fr.fieldInfo.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                    if ("0.0.1".equals(d.statementId())) {
                        assertEquals("instance type PrintStream", d.currentValue().toString());
                        assertDv(d, 0, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(EXTERNAL_NOT_NULL));
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("instance type PrintStream", d.currentValue().toString());
                        assertDv(d, 0, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(EXTERNAL_NOT_NULL));
                    }
                }

                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("p", d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "b?p:<f:i>" : "b?p:0";
                        assertEquals(expect, d.currentValue().toString());
                    }
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
                    if ("1.0.3".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "j:0" : "i$1:1,j:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (I0_FQN.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.iteration() > 0); // does not exist earlier!

                        assertEquals("i$0:0,this.i:0", d.variableInfo().getLinkedVariables().toString());
                        assertEquals("instance type int", d.currentValue().toString());
                    }
                    if ("1.0.3".equals(d.statementId())) {
                        // NOTE: it is fine to have i$1 here, as long as it is not with a :0
                        assertEquals("i$0:0,i$1:1,j:0", d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (I1_FQN.equals(d.variableName())) {
                    // exists from 1.0.0 onwards
                    assertTrue(d.iteration() > 0); // does not exist earlier!
                    assertEquals("instance type int", d.currentValue().toString());
                    if ("1.0.0".equals(d.statementId())) {
                        // after the assignment, i becomes a different value
                        String expectLv = "i$1:0,j:0,this.i:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString(), d.statementId());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        // after the assignment, i becomes a different value
                        assertEquals("i$1:0,j:0", d.variableInfo().getLinkedVariables().toString(), d.statementId());
                    }
                    if ("1.0.3".equals(d.statementId())) {
                        assertEquals("i$1:0,j:0", d.variableInfo().getLinkedVariables().toString(), d.statementId());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("i$1:0", d.variableInfo().getLinkedVariables().toString(), d.statementId());
                    }
                }
                if (I101_FQN.equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertFalse(d.variableInfoContainer().hasEvaluation());
                    }
                    // is primitive
                    if ("1.0.0".equals(d.statementId()) || "1.0.1".equals(d.statementId())) {
                        fail("Should not follow the path 102-103-1M-new it-1E-100");
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        assertEquals("1+" + I1, d.currentValue().toString());
                        assertEquals("1.0.2-C", d.variableInfo().getReadId());
                    }
                    if ("1.0.3".equals(d.statementId())) {
                        assertEquals("1+" + I1, d.currentValue().toString());
                        assertEquals("1.0.2-C", d.variableInfo().getReadId());
                    }
                    assertEquals("i$1$1.0.1-E:0,this.i:0", d.variableInfo().getLinkedVariables().toString());
                }
                if (I.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? I_DELAYED : "instance type int";
                        assertEquals(expect, d.currentValue().toString());
                        int expectStatementTime = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 0;
                        assertEquals(expectStatementTime, d.variableInfo().getStatementTime());
                        assertEquals("[0]", d.variableInfo().getReadAtStatementTimes().toString());

                        assertEquals("this.i:0", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? I_DELAYED : "instance type int";
                        assertEquals(expect, d.currentValue().toString());
                        int expectStatementTime = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 1;
                        assertEquals(expectStatementTime, d.variableInfo().getStatementTime());
                        assertEquals("[1]", d.variableInfo().getReadAtStatementTimes().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        // we switch to NOT_INVOLVED, given that the field has been assigned; its external value is of no use
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(EXTERNAL_NOT_NULL));
                        assertEquals("this.i:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1+<f:i>" : "1+" + I1;
                        assertEquals(expect, d.currentValue().toString());
                        assertEquals("1.0.2-E", d.variableInfo().getReadId());
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(EXTERNAL_NOT_NULL));
                        String expectLv = "this.i:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.3".equals(d.statementId())) {
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(EXTERNAL_NOT_NULL));
                        assertEquals("this.i:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1+<f:i>" : "1+" + I1;
                        assertEquals(expect, d.currentValue().toString());
                        assertEquals("this.i:0", d.variableInfo().getLinkedVariables().toString());
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(EXTERNAL_NOT_NULL));
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int timeI = d.statementAnalysis().statementTime(VariableInfoContainer.Level.INITIAL);
            int timeE = d.statementAnalysis().statementTime(VariableInfoContainer.Level.EVALUATION);
            int timeM = d.statementAnalysis().statementTime(VariableInfoContainer.Level.MERGE);

            // method itself is synchronised, so statement time stands still
            if ("increment".equals(d.methodInfo().name)) {
                assertEquals(0, timeI);
                assertEquals(0, timeE);
                assertEquals(0, timeM);
            }
            if ("increment2".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(0, timeI);
                    assertEquals(1, timeE);
                    assertEquals(1, timeM);
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(1, timeI);
                    assertEquals(1, timeE);
                    assertEquals(1, timeM);
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
                        .getVariable(INC3_RETURN_VAR).current().getValue().toString());
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
                    String linkedVariables = d.variableInfo().getLinkedVariables().toString();
                    if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "j:0,this.i:0" : "i$1:1,j:0,this.i:0";
                        assertEquals(expectLv, linkedVariables, d.statementId());
                    }
                    if ("3".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:i>" : I1;
                        assertEquals(expectValue, d.currentValue().toString());
                        String expectLv = d.iteration() == 0 ? "j:0,this.i:0" : "i$1:1,i$2:1,j:0,k:0,this.i:0";
                        assertEquals(expectLv, linkedVariables, d.statementId());
                    }
                    if ("4.0.0.0.0".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "j:0,k:0,this.i:0" : "i$1:1,i$2:1,j0:0,j:0,k:0,this.i:0";
                        assertEquals(expectLv, linkedVariables, d.statementId());
                    }
                    if ("4.0.0.0.1".equals(d.statementId()) || "4.0.0.0.2".equals(d.statementId()) ||
                            "4.0.0.0.3".equals(d.statementId()) || "4.0.0.0.4".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "j0:0,j:0,k:0" : "i$1:1,i$2:1,j0:0,j:0,k:0";
                        assertEquals(expectLv, linkedVariables, "At " + d.statementId());
                    }
                    if ("4.0.0".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "j:0,k:0" : "i$1:1,i$2:1,j:0,k:0";
                        assertEquals(expectLv, linkedVariables, "At " + d.statementId());
                    }
                    if ("4".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "j:0,k:0,this.i:0" : "i$1:1,i$2:1,j:0,k:0,this.i:0";
                        assertEquals(expectLv, linkedVariables, d.statementId());
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
                    if (I1.equals(d.variable().simpleName())) {
                        if ("3".equals(d.statementId())) {
                            assertEquals("i$1:0,i$2:1,j:1,k:1,this.i:1", d.variableInfo().getLinkedVariables().toString());
                            assertFalse(d.variableInfo().getLinkedVariables().isDelayed());
                            assertEquals(DV.FALSE_DV, d.getProperty(CONTEXT_MODIFIED));
                        }
                        if ("4.0.0.0.0".equals(d.statementId())) {
                            assertEquals("i$1:0,i$2:1,j0:1,j:1,k:1,this.i:1", d.variableInfo().getLinkedVariables().toString());
                            assertFalse(d.variableInfo().getLinkedVariables().isDelayed());
                            assertEquals(DV.FALSE_DV, d.getProperty(CONTEXT_MODIFIED));
                        }
                        if ("4".equals(d.statementId())) {
                            assertEquals("i$1:0,i$2:1,j:1,k:1,this.i:1", d.variableInfo().getLinkedVariables().toString());
                            assertFalse(d.variableInfo().getLinkedVariables().isDelayed());
                            assertEquals(DV.FALSE_DV, d.getProperty(CONTEXT_MODIFIED));
                        }
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
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
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
            int timeI = d.statementAnalysis().statementTime(VariableInfoContainer.Level.INITIAL);
            int timeE = d.statementAnalysis().statementTime(VariableInfoContainer.Level.EVALUATION);
            int timeM = d.statementAnalysis().statementTime(VariableInfoContainer.Level.MERGE);

            if ("test4".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(0, timeI);
                    assertEquals(1, timeE);
                    assertEquals(1, timeM);
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(1, timeI);
                    assertEquals(1, timeE);
                    assertEquals(1, timeM);
                }
                if ("2".equals(d.statementId())) {
                    assertEquals(1, timeI);
                    assertEquals(2, timeE);
                    assertEquals(2, timeM);
                }
            }
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
