
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
import org.e2immu.analyser.model.expression.EmptyExpression;
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
        final String THIS = TYPE + ".this";
        final String OUT = "java.lang.System.out";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("setS1".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        Assert.assertSame(EmptyExpression.NO_VALUE, d.evaluationResult().value());
                    } else {
                        Assert.assertSame(EmptyExpression.NO_RETURN_VALUE, d.evaluationResult().value());
                    }
                }
                if ("1".equals(d.statementId())) {
                    // should not be sth like null != s$2, because statement time has not advanced since the assignments
                    Assert.assertEquals("true", d.evaluationResult().value().debugOutput());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setS1".equals(d.methodInfo().name) && THIS.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfoContainer().hasMerge());
                    String expectValue = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "instance type Basics_3";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
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
                if (d.iteration() == 0) {
                    Assert.assertSame(EmptyExpression.NO_VALUE, d.currentValue());
                } else {
                    if("0.0.0".equals(d.statementId())) {
                        // because of the modifying method println
                        Assert.assertEquals("instance type PrintStream", d.currentValue().toString());
                    } else if ("0".equals(d.statementId())) {
                        Assert.assertEquals("input1.contains(\"a\")?instance type PrintStream:nullable instance type PrintStream", d.currentValue().toString());
                    }
                }
            }
            if ("setS1".equals(d.methodInfo().name) && S.equals(d.variableName())) {
                if (d.iteration() == 0) {
                    Assert.assertSame(d.statementId(), LinkedVariables.DELAY, d.variableInfo().getLinkedVariables());
                }
                if ("0.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "nullable? instance type String";
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
                }
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("input1.contains(\"a\")?\"xyz\":\"abc\"", d.currentValue().toString());
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
                Assert.assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL));
                Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                Assert.assertNull(d.fieldAnalysis().getEffectivelyFinalValue());
            }
        };

        testClass("Basics_3", 0, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
