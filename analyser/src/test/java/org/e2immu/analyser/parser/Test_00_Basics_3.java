
/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/*
Aims to catch problems in assigning properties from the field to the variable in statement 0, setS1.
The values there are a summary of what happened deeper down, which is different from what is in the field.
(The field cannot be @NotNull but locally we know s will not be null at that point.)

API annotations are not loaded, so we don't know that System.out is never null.

 */
public class Test_00_Basics_3 extends CommonTestRunner {
    private static final String E = VariableInfoContainer.Level.EVALUATION.toString();
    private static final String C = VariableInfoContainer.Level.INITIAL.toString();
    private static final String M = VariableInfoContainer.Level.MERGE.toString();

    // not loading in the AnnotatedAPIs, so System.out will have @Modified=1 after println()
    // this also causes a potential null pointer exception, as we don't know if out will be @NotNull

    @Test
    public void test() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Basics_3";
        final String S = TYPE + ".s";
        final String S_0 = TYPE + ".s$0";
        final String THIS = TYPE + ".this";
        final String OUT = "java.lang.System.out";
        final String GET_S_RET_VAR = TYPE + ".getS()";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("setS1".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        Assert.assertTrue(d.evaluationResult().someValueWasDelayed());
                    } else {
                        Assert.assertSame(EmptyExpression.NO_RETURN_VALUE, d.evaluationResult().value());
                    }
                }
                if ("1".equals(d.statementId())) {
                    // should not be sth like null != s$2, because statement time has not advanced since the assignments
                    Assert.assertEquals("true", d.evaluationResult().value().debugOutput());
                }
            }
            if ("getS".equals(d.methodInfo().name)) {
                if (d.iteration() > 0) {
                    Assert.assertEquals(S_0, d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setS1".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo pi && "input1".equals(pi.name)) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
            }
            if ("setS1".equals(d.methodInfo().name) && THIS.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfoContainer().hasMerge());

                    Assert.assertEquals("instance type Basics_3", d.currentValue().toString());
                }
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals(VariableInfoContainer.NOT_YET_ASSIGNED, d.variableInfo().getAssignmentId());
                    Assert.assertEquals("0.0.0" + E, d.variableInfo().getReadId());
                    if (d.iteration() > 0) {
                        Assert.assertEquals("instance type Basics_3", d.currentValue().toString());
                    }
                }
                if ("0.1.0".equals(d.statementId())) {
                    Assert.assertEquals(VariableInfoContainer.NOT_YET_ASSIGNED, d.variableInfo().getAssignmentId());
                    Assert.assertEquals("0.1.0" + E, d.variableInfo().getReadId());
                    if (d.iteration() > 0) {
                        Assert.assertEquals("instance type Basics_3", d.currentValue().toString());
                    }
                }
            }
            if ("setS1".equals(d.methodInfo().name) && OUT.equals(d.variableName())) {

                if ("0.0.0".equals(d.statementId())) {
                    // because of the modifying method println
                    if (d.iteration() == 0) {
                        Assert.assertTrue(d.currentValueIsDelayed());
                    } else {
                        Assert.assertEquals("instance type PrintStream", d.currentValue().toString());
                    }
                } else if ("0.1.0".equals(d.statementId())) {
                    Assert.assertTrue(d.iteration() > 0);
                    Assert.assertEquals(VariableInfoContainer.NOT_YET_READ, d.variableInfo().getReadId());
                } else if ("0".equals(d.statementId())) {
                    Assert.assertEquals("nullable instance type PrintStream",
                            d.variableInfoContainer().getPreviousOrInitial().getValue().toString());
                    String expectValue = d.iteration() == 0 ? "<field:java.lang.System.out>" : "instance type PrintStream";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                }

                // completely independent of the iterations, we always should have @NotNull because of context
                if ("0.0.0".equals(d.statementId()) || "0".equals(d.statementId())) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
            }
            if ("setS1".equals(d.methodInfo().name) && S.equals(d.variableName())) {
                if (d.iteration() == 0) {
                    Assert.assertSame(d.statementId(), LinkedVariables.DELAY, d.variableInfo().getLinkedVariables());
                }
                if ("0.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<field:org.e2immu.analyser.testexample.Basics_3.s>"
                            : "nullable? instance type String";
                    Assert.assertEquals(expectValue, d.currentValue().debugOutput());
                    Assert.assertFalse(d.variableInfo().isAssigned());
                    if (d.iteration() == 0) {
                        Assert.assertEquals(VariableInfoContainer.VARIABLE_FIELD_DELAY, d.variableInfo().getStatementTime());
                    } else {
                        Assert.assertEquals(1, d.variableInfo().getStatementTime());
                        // s is linked to s$1
                        Assert.assertEquals("org.e2immu.analyser.testexample.Basics_3.s$1",
                                d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("0.0.1".equals(d.statementId())) {
                    Assert.assertEquals("\"xyz\"", d.currentValue().debugOutput());
                    Assert.assertTrue(d.variableInfo().isAssigned());
                    if (d.iteration() == 0) {
                        Assert.assertEquals(VariableInfoContainer.VARIABLE_FIELD_DELAY, d.variableInfo().getStatementTime());
                    } else {
                        Assert.assertEquals(2, d.variableInfo().getStatementTime());
                        String expectedLinked = "org.e2immu.analyser.testexample.Basics_3.s$1";
                        Assert.assertEquals(expectedLinked, d.variableInfo().getLinkedVariables().toString());
                    }
                    // not null of assignment
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
                if ("0.1.0".equals(d.statementId())) {
                    Assert.assertEquals("\"abc\"", d.currentValue().debugOutput());
                    Assert.assertTrue(d.variableInfo().isAssigned());
                    if (d.iteration() == 0) {
                        Assert.assertEquals(VariableInfoContainer.VARIABLE_FIELD_DELAY, d.variableInfo().getStatementTime());
                    } else {
                        Assert.assertEquals(1, d.variableInfo().getStatementTime());
                        Assert.assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                    }
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("input1.contains(\"a\")?\"xyz\":\"abc\"", d.currentValue().toString());
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));

                    if (d.iteration() == 0) {
                        Assert.assertEquals(VariableInfoContainer.VARIABLE_FIELD_DELAY, d.variableInfo().getStatementTime());
                    } else {
                        VariableInfo vi1 = d.variableInfoContainer().getPreviousOrInitial();
                        Assert.assertEquals(0, vi1.getStatementTime());
                        Assert.assertEquals(2, d.variableInfo().getStatementTime());

                        String expectedLinked = "org.e2immu.analyser.testexample.Basics_3.s$1";
                        Assert.assertEquals(expectedLinked, d.variableInfo().getLinkedVariables().toString());
                    }
                    Assert.assertTrue("At " + d.statementId(), d.variableInfo().isAssigned());
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("input1.contains(\"a\")?\"xyz\":\"abc\"", d.currentValue().toString());
                    if (d.iteration() > 0) {
                        // linked to s$1 and s$2$0:M
                        Assert.assertEquals("org.e2immu.analyser.testexample.Basics_3.s$1,org.e2immu.analyser.testexample.Basics_3.s$2$0" + M,
                                d.variableInfo().getLinkedVariables().toString());
                    }
                    Assert.assertTrue("At " + d.statementId(), d.variableInfo().isAssigned());

                    int expectContentModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                    Assert.assertEquals(expectContentModified, d.getProperty(VariableProperty.CONTEXT_MODIFIED));

                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
            if ("setS2".equals(d.methodInfo().name) && S.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfo().isAssigned());
                    Assert.assertEquals("input2", d.currentValue().toString());
                    // not linked to input2, @E2Immutable
                    if (d.iteration() == 0) {
                        Assert.assertSame(LinkedVariables.DELAY, d.variableInfo().getLinkedVariables());
                    } else {
                        Assert.assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                    }
                    int expectContentModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                    Assert.assertEquals(expectContentModified, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                    //      Assert.assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    //      Assert.assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL_VARIABLE));
                }
            }
            if ("getS".equals(d.methodInfo().name)) {
                if (S.equals(d.variableName())) {
                    String expectS = d.iteration() == 0 ? "<field:org.e2immu.analyser.testexample.Basics_3.s>"
                            : "nullable? instance type String";
                    Assert.assertEquals(expectS, d.currentValue().toString());
                    String expectLinkedVars = d.iteration() == 0 ? LinkedVariables.DELAY_STRING :
                            "org.e2immu.analyser.testexample.Basics_3.s$0";
                    Assert.assertEquals(expectLinkedVars, d.variableInfo().getLinkedVariables().toString());

                    int expectExternalNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                    Assert.assertEquals(expectExternalNN, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    Assert.assertEquals(expectExternalNN, d.getProperty(VariableProperty.NOT_NULL_VARIABLE));

                    int expectModifiedOutside = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    Assert.assertEquals(expectModifiedOutside, d.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
                    int expectContentModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                    Assert.assertEquals(expectContentModified, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    // summary of the two above
                    Assert.assertEquals(expectModifiedOutside, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                }
                if (S_0.equals(d.variableName())) {
                    assert d.iteration() > 0;
                    Assert.assertEquals("nullable? instance type String", d.currentValue().toString());
                    Assert.assertEquals("this.s", d.variableInfo().getLinkedVariables().toString());

                    Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_VARIABLE));
                }
                if(d.variable() instanceof ReturnVariable) {
                    Assert.assertEquals(GET_S_RET_VAR, d.variableName());
                    // copied from S
                    int expectExternalNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                    Assert.assertEquals(expectExternalNN, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    Assert.assertEquals(expectExternalNN, d.getProperty(VariableProperty.NOT_NULL_VARIABLE));
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
                    Assert.assertEquals(0, time1);
                    Assert.assertEquals(1, time3);
                    Assert.assertEquals(2, time4); // merge
                    Assert.assertEquals("0" + C, flowData.assignmentIdOfStatementTime.get(0));
                    Assert.assertEquals("0" + E, flowData.assignmentIdOfStatementTime.get(1));
                    Assert.assertEquals("0" + M, flowData.assignmentIdOfStatementTime.get(2));
                }
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals(1, time1);
                    Assert.assertEquals(2, time3);
                    Assert.assertEquals(2, time4);
                    Assert.assertEquals("0" + C, flowData.assignmentIdOfStatementTime.get(0));
                    Assert.assertEquals("0" + E, flowData.assignmentIdOfStatementTime.get(1));
                    Assert.assertEquals("0.0.0" + E, flowData.assignmentIdOfStatementTime.get(2));
                    Assert.assertEquals("true", d.state().toString());
                    Assert.assertEquals("input1.contains(\"a\")", d.condition().toString());
                    Assert.assertEquals("input1.contains(\"a\")", d.absoluteState().toString());
                    Assert.assertEquals("true", d.localConditionManager().precondition().toString());
                    Assert.assertFalse(d.localConditionManager().isDelayed());
                    Assert.assertEquals("true", d.statementAnalysis().stateData.getPrecondition().toString());
                }
                if ("0.0.1".equals(d.statementId())) { // first assignment
                    Assert.assertEquals(2, time1);
                    Assert.assertEquals(2, time3);
                    Assert.assertEquals(2, time4);
                    Assert.assertEquals("0" + C, flowData.assignmentIdOfStatementTime.get(0));
                    Assert.assertEquals("0" + E, flowData.assignmentIdOfStatementTime.get(1));
                    Assert.assertEquals("0.0.0" + E, flowData.assignmentIdOfStatementTime.get(2));
                }
                if ("0.1.0".equals(d.statementId())) { // second assignment
                    Assert.assertEquals(1, time1);
                    Assert.assertEquals(1, time3);
                    Assert.assertEquals(1, time4);
                    Assert.assertEquals("0" + C, flowData.assignmentIdOfStatementTime.get(0));
                    Assert.assertEquals("0" + E, flowData.assignmentIdOfStatementTime.get(1));
                    Assert.assertEquals("true", d.state().toString());
                    Assert.assertEquals("!input1.contains(\"a\")", d.condition().toString());
                    Assert.assertEquals("!input1.contains(\"a\")", d.absoluteState().toString());
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals(2, time1);
                    Assert.assertEquals(2, time3);
                    Assert.assertEquals(2, time4);
                    Assert.assertEquals("0" + C, flowData.assignmentIdOfStatementTime.get(0));
                    Assert.assertEquals("0" + E, flowData.assignmentIdOfStatementTime.get(1));
                    Assert.assertEquals("0" + M, flowData.assignmentIdOfStatementTime.get(2));
                    if (d.iteration() > 0) {
                        Assert.assertNotNull(d.haveError(Message.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s".equals(d.fieldInfo().name) && d.iteration() > 0) {
                if (d.iteration() > 1) {
                    Assert.assertTrue(d.fieldAnalysis().getLinkedVariables().isEmpty());
                }
                Assert.assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
                Assert.assertNull(d.fieldAnalysis().getEffectivelyFinalValue());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setS1".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, p0.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                int expectExternalNotNull = d.iteration() <= 1 ? Level.DELAY : MultiLevel.NULLABLE;
                Assert.assertEquals(expectExternalNotNull, p0.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
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
