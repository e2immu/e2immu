
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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/*
Aims to catch problems in assigning properties from the field to the variable in statement 0, setS1.
The values there are a summary of what happened deeper down, which is different from what is in the field.
(The field cannot be @NotNull, but locally we know s will not be null at that point.)

API annotations are not loaded, so we don't know that System.out is never null.

 */
public class Test_00_Basics_3 extends CommonTestRunner {
    private static final String E = VariableInfoContainer.Level.EVALUATION.toString();
    private static final String C = VariableInfoContainer.Level.INITIAL.toString();
    private static final String M = VariableInfoContainer.Level.MERGE.toString();
    public static final String INSTANCE_PRINT_STREAM = "nullable instance type PrintStream";

    // not loading in the AnnotatedAPIs, so System.out will have @Modified=1 after println()
    // this also causes a potential null pointer exception, as we don't know if out will be @NotNull

    @Test
    public void test() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Basics_3";
        final String S = TYPE + ".s";
        final String S_0 = "s$0";
        final String THIS = TYPE + ".this";
        final String OUT = "java.lang.System.out";
        final String GET_S_RET_VAR = TYPE + ".getS()";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("setS1".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertTrue(d.evaluationResult().someValueWasDelayed());
                    } else {
                        assertSame(EmptyExpression.NO_RETURN_VALUE, d.evaluationResult().value());
                    }
                }
                if ("1".equals(d.statementId())) {
                    // should not be sth like null != s$2, because statement time has not advanced since the assignments
                    String expect = d.iteration() == 0 ? "null!=<field:org.e2immu.analyser.testexample.Basics_3.s>" : "true";
                    assertEquals(expect, d.evaluationResult().value().debugOutput());
                }
            }
            if ("getS".equals(d.methodInfo().name)) {
                String expectValue = d.iteration() == 0 ? "<f:s>" : S_0;
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setS1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "input1".equals(pi.name)) {
                    // statement independent, as the only occurrence of input1 is in evaluation of "0", before "0.0.0" etc.
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));

                    int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                    assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                }
                if (THIS.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasMerge());
                        assertEquals("instance type Basics_3", d.currentValue().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        assertTrue(d.variableInfo().getAssignmentIds().hasNotYetBeenAssigned());
                        assertEquals("0.0.0" + E, d.variableInfo().getReadId());
                        if (d.iteration() > 0) {
                            assertEquals("instance type Basics_3", d.currentValue().toString());
                        }
                    }
                    if ("0.1.0".equals(d.statementId())) {
                        assertTrue(d.variableInfo().getAssignmentIds().hasNotYetBeenAssigned());
                        assertEquals("0.1.0" + E, d.variableInfo().getReadId());
                        if (d.iteration() > 0) {
                            assertEquals("instance type Basics_3", d.currentValue().toString());
                        }
                    }
                    LinkedVariables expectedLinked1 = d.iteration() == 0 ? LinkedVariables.DELAYED_EMPTY : LinkedVariables.EMPTY;
                    assertEquals(expectedLinked1, d.variableInfo().getLinked1Variables());
                }
                if (OUT.equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        // because of the modifying method println
                        if (d.iteration() == 0) {
                            assertTrue(d.currentValueIsDelayed());
                        } else {
                            assertEquals(INSTANCE_PRINT_STREAM, d.currentValue().toString());
                        }
                        assertEquals(VariableInfoContainer.NOT_A_VARIABLE_FIELD, d.variableInfo().getStatementTime());
                        int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                        assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    } else if ("0.1.0".equals(d.statementId())) {
                        assertTrue(d.iteration() > 0);
                        assertEquals(VariableInfoContainer.NOT_YET_READ, d.variableInfo().getReadId());
                        assertEquals(VariableInfoContainer.NOT_A_VARIABLE_FIELD, d.variableInfo().getStatementTime());
                    } else if ("0".equals(d.statementId())) {
                        assertEquals(INSTANCE_PRINT_STREAM,
                                d.variableInfoContainer().getPreviousOrInitial().getValue().toString());
                        String expectValue = d.iteration() == 0 ? "<field:java.lang.System.out>" : INSTANCE_PRINT_STREAM;
                        assertEquals(expectValue, d.currentValue().debugOutput());
                        assertEquals(VariableInfoContainer.NOT_A_VARIABLE_FIELD, d.variableInfo().getStatementTime());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));

                        int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                        assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }

                    // completely independent of the iterations, we always should have @NotNull because of context
                    if ("0.0.0".equals(d.statementId()) || "0".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                }
                if (S.equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:s>" : "nullable instance type String";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertFalse(d.variableInfo().isAssigned());
                        if (d.iteration() == 0) {
                            assertEquals(VariableInfoContainer.VARIABLE_FIELD_DELAY, d.variableInfo().getStatementTime());
                        } else {
                            assertEquals(1, d.variableInfo().getStatementTime());
                        }
                        assertEquals("", d.variableInfo().getStaticallyAssignedVariables().toString());
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertEquals("\"xyz\"", d.currentValue().debugOutput());
                        assertTrue(d.variableInfo().isAssigned());
                        if (d.iteration() == 0) {
                            assertEquals(VariableInfoContainer.VARIABLE_FIELD_DELAY, d.variableInfo().getStatementTime());
                        } else {
                            assertEquals(2, d.variableInfo().getStatementTime());
                        }
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        // not null of assignment
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                    if ("0.1.0".equals(d.statementId())) {
                        assertEquals("\"abc\"", d.currentValue().debugOutput());
                        assertTrue(d.variableInfo().isAssigned());
                        if (d.iteration() == 0) {
                            assertEquals(VariableInfoContainer.VARIABLE_FIELD_DELAY, d.variableInfo().getStatementTime());
                        } else {
                            assertEquals(1, d.variableInfo().getStatementTime());
                        }
                        assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    }
                    if ("0".equals(d.statementId())) {
                        assertEquals("input1.contains(\"a\")?\"xyz\":\"abc\"", d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));

                        if (d.iteration() == 0) {
                            assertEquals(VariableInfoContainer.VARIABLE_FIELD_DELAY, d.variableInfo().getStatementTime());
                        } else {
                            VariableInfo vi1 = d.variableInfoContainer().getPreviousOrInitial();
                            assertEquals(0, vi1.getStatementTime());
                            assertEquals(2, d.variableInfo().getStatementTime());

                        }
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        assertTrue(d.variableInfo().isAssigned());
                        assertEquals("0.0.1-E,0.1.0-E,0:M", d.variableInfo().getAssignmentIds().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<f:s>" : "input1.contains(\"a\")?\"xyz\":\"abc\"";
                        assertEquals(expected, d.currentValue().toString());

                        assertEquals("", d.variableInfo().getStaticallyAssignedVariables().toString());
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        assertEquals("0.0.1-E,0.1.0-E,0:M", d.variableInfo().getAssignmentIds().toString());

                        int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
                if ((TYPE + ".s$2$0:M").equals(d.variableName())) {
                    assertTrue(d.iteration() > 0);
                    assertEquals("1", d.statementId());
                    assertEquals("input1.contains(\"a\")?\"xyz\":\"abc\"", d.currentValue().toString());
                }
            }
            if ("setS2".equals(d.methodInfo().name) && S.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertTrue(d.variableInfo().isAssigned());
                    assertEquals("input2", d.currentValue().toString());
                    assertTrue(d.variableInfo().getLinkedVariables().isEmpty());

                    assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
            }
            if ("getS".equals(d.methodInfo().name)) {
                if (S.equals(d.variableName())) {
                    String expectS = d.iteration() == 0 ? "<f:s>" : "nullable instance type String";
                    assertEquals(expectS, d.currentValue().toString());
                    assertEquals("", d.variableInfo().getStaticallyAssignedVariables().toString());

                    int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                    assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
                if (S_0.equals(d.variableName())) {
                    assert d.iteration() > 0;
                    assertEquals("nullable instance type String", d.currentValue().toString());
                    assertEquals("this.s", d.variableInfo().getStaticallyAssignedVariables().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());

                    assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals(GET_S_RET_VAR, d.variableName());
                    String expectValue = d.iteration() == 0 ? "<f:s>" : S_0;
                    assertEquals(expectValue, d.currentValue().toString());

                    // copied from S
                    int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                    assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setS1".equals(d.methodInfo().name)) {
                FlowData flowData = d.statementAnalysis().flowData;
                int time1 = flowData.getInitialTime();
                int time3 = flowData.getTimeAfterEvaluation();
                int time4 = flowData.getTimeAfterSubBlocks();
                if ("0".equals(d.statementId())) {
                    assertEquals(0, time1);
                    assertEquals(1, time3);
                    assertEquals(2, time4); // merge
                    assertEquals("0" + C, flowData.assignmentIdOfStatementTime.get(0));
                    assertEquals("0" + E, flowData.assignmentIdOfStatementTime.get(1));
                    assertEquals("0" + M, flowData.assignmentIdOfStatementTime.get(2));
                }
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals(1, time1);
                    assertEquals(2, time3);
                    assertEquals(2, time4);
                    assertEquals("0" + C, flowData.assignmentIdOfStatementTime.get(0));
                    assertEquals("0" + E, flowData.assignmentIdOfStatementTime.get(1));
                    assertEquals("0.0.0" + E, flowData.assignmentIdOfStatementTime.get(2));
                    assertEquals("true", d.state().toString());
                    assertEquals("input1.contains(\"a\")", d.condition().toString());
                    assertEquals("input1.contains(\"a\")", d.absoluteState().toString());
                    assertEquals("true", d.localConditionManager().precondition().expression().toString());
                    assertFalse(d.localConditionManager().isDelayed());
                    assertTrue(d.statementAnalysis().stateData.getPrecondition().isEmpty());
                }
                if ("0.0.1".equals(d.statementId())) { // first assignment
                    assertEquals(2, time1);
                    assertEquals(2, time3);
                    assertEquals(2, time4);
                    assertEquals("0" + C, flowData.assignmentIdOfStatementTime.get(0));
                    assertEquals("0" + E, flowData.assignmentIdOfStatementTime.get(1));
                    assertEquals("0.0.0" + E, flowData.assignmentIdOfStatementTime.get(2));
                }
                if ("0.1.0".equals(d.statementId())) { // second assignment
                    assertEquals(1, time1);
                    assertEquals(1, time3);
                    assertEquals(1, time4);
                    assertEquals("0" + C, flowData.assignmentIdOfStatementTime.get(0));
                    assertEquals("0" + E, flowData.assignmentIdOfStatementTime.get(1));
                    assertEquals("true", d.state().toString());
                    assertEquals("!input1.contains(\"a\")", d.condition().toString());
                    assertEquals("!input1.contains(\"a\")", d.absoluteState().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(2, time1);
                    assertEquals(2, time3);
                    assertEquals(2, time4);
                    assertEquals("0" + C, flowData.assignmentIdOfStatementTime.get(0));
                    assertEquals("0" + E, flowData.assignmentIdOfStatementTime.get(1));
                    assertEquals("0" + M, flowData.assignmentIdOfStatementTime.get(2));
                    if (d.iteration() > 0) {
                        assertNotNull(d.haveError(Message.Label.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s".equals(d.fieldInfo().name)) {
                String expect = d.iteration() == 0 ? "<f:s>,input2,null" : "input1.contains(\"a\")?\"xyz\":\"abc\",input2,null";
                assertEquals(expect, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());

                assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectModified, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
                assertEquals("<variable value>", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                assertEquals("", d.fieldAnalysis().getLinkedVariables().toString());
                assertEquals("", d.fieldAnalysis().getLinked1Variables().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setS1".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectCnn, p0.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                int expectExternalNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.DELAY;
                assertEquals(expectExternalNotNull, p0.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
        };

        testClass("Basics_3", 0, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
