
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

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintStream;

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
                    String expect = d.iteration() == 0 ? "1+<field:org.e2immu.analyser.testexample.Basics_4.i>" :
                            "1+org.e2immu.analyser.testexample.Basics_4.i$0";
                    Assert.assertEquals(expect, d.currentValue().toString());
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
        testClass("Basics_5", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // basic statement timing
    @Test
    public void test6() throws IOException {

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("field".equals(d.fieldInfo().name)) {
                Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            }
        };
        final String TYPE = "org.e2immu.analyser.testexample.Basics_6";
        final String FIELD = TYPE + ".field";
        final String FIELD_0 = TYPE + ".field$0";
        final String FIELD_1 = TYPE + ".field$1";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setField".equals(d.methodInfo().name) && d.variableInfo().variable() instanceof ParameterInfo) {
                Assert.assertSame(LinkedVariables.EMPTY, d.variableInfoContainer().getPreviousOrInitial().getLinkedVariables());
                Assert.assertEquals(LinkedVariables.EMPTY, d.variableInfo().getLinkedVariables());
            }
            if ("test1".equals(d.methodInfo().name)) {
                if (FIELD.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        int expect = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 0;
                        Assert.assertEquals(expect, d.variableInfo().getStatementTime());
                    }
                    if ("1".equals(d.statementId())) {
                        int expect = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 0;
                        Assert.assertEquals(expect, d.variableInfo().getStatementTime());
                    }
                    if ("2".equals(d.statementId())) {
                        Assert.assertEquals("[1]", d.variableInfo().getReadAtStatementTimes().toString());
                        int expect = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 1;
                        Assert.assertEquals(expect, d.variableInfo().getStatementTime());
                    }
                }
                if (FIELD_0.equals(d.variableName())) {
                    Assert.assertTrue(d.iteration() > 0);
                    Assert.assertEquals("nullable? instance type String", d.currentValue().toString());
                    Assert.assertEquals(VariableInfoContainer.NOT_A_VARIABLE_FIELD, d.variableInfo().getStatementTime());
                }
                if (FIELD_1.equals(d.variableName())) {
                    Assert.assertTrue(d.iteration() > 0);
                    Assert.assertEquals("nullable? instance type String", d.currentValue().toString());
                    Assert.assertEquals(VariableInfoContainer.NOT_A_VARIABLE_FIELD, d.variableInfo().getStatementTime());
                    Assert.assertTrue("Have " + d.statementId(), "2".compareTo(d.statementId()) <= 0);
                }
                if ("v1".equals(d.variableName()) && "0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<field:org.e2immu.analyser.testexample.Basics_6.field>" : FIELD_0;
                    Assert.assertEquals(expect, d.currentValue().toString());
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
                        Assert.assertEquals(3, d.statementAnalysis().variables.size());
                        Assert.assertEquals(0, timeI);
                        Assert.assertEquals(0, timeE);
                        Assert.assertEquals(0, timeM);
                    }
                    if ("1".equals(d.statementId())) {
                        Assert.assertEquals(0, timeI);
                        Assert.assertEquals(1, timeE);
                        Assert.assertEquals(1, timeM);
                        // 4 vars: field, this, v1, out
                        Assert.assertEquals(4, d.statementAnalysis().variables.size());
                    }
                    if ("2".equals(d.statementId())) {
                        Assert.assertEquals(1, timeI);
                        Assert.assertEquals(1, timeE);
                        Assert.assertEquals(1, timeM);
                        // 5 vars: field, this, v1, v2, out
                        Assert.assertEquals(5, d.statementAnalysis().variables.size());
                    }
                    if ("3".equals(d.statementId())) {
                        Assert.assertEquals(1, timeI);
                        Assert.assertEquals(1, timeE);
                        Assert.assertEquals(1, timeM);
                        // 5 vars: field, this, v1, v2, out
                        Assert.assertEquals(5, d.statementAnalysis().variables.size());
                    }
                } else if (d.iteration() == 1) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertEquals(4, d.statementAnalysis().variables.size());
                    }
                }
                if ("3".equals(d.statementId())) {
                    Assert.assertNull(d.haveError(Message.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                }
            }
            if ("test2".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId()) && d.iteration() > 0) {
                    // this, field, field$0, v1
                    Assert.assertEquals(4, d.statementAnalysis().variables.size());
                }
                if ("1".equals(d.statementId()) && d.iteration() > 0) {
                    // this, field, field$0, v1, v2
                    Assert.assertEquals(5, d.statementAnalysis().variables.size());
                    Expression valueV1 = d.statementAnalysis().variables.get("v1").current().getValue();
                    Expression valueV2 = d.statementAnalysis().variables.get("v2").current().getValue();
                    Assert.assertTrue(valueV1 instanceof VariableExpression);
                    Assert.assertTrue(valueV2 instanceof VariableExpression);
                    Variable v1Redirected = ((VariableExpression) valueV1).variable();
                    Variable v2Redirected = ((VariableExpression) valueV2).variable();
                    Assert.assertEquals(v1Redirected, v2Redirected);
                }
                if ("2".equals(d.statementId()) && d.iteration() > 0) {
                    Assert.assertNotNull(d.haveError(Message.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                }
            }
            if ("test4".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals(0, timeI);
                    Assert.assertEquals(1, timeE);
                    Assert.assertEquals(1, timeM);
                }
                if (d.iteration() > 0) {
                    Assert.assertNull(d.haveError(Message.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                }
            }
            if ("test3".equals(d.methodInfo().name)) {
                Assert.assertEquals(0, timeI);
                Assert.assertEquals(0, timeE);
                Assert.assertEquals(0, timeM);

                if ("3".equals(d.statementId()) && d.iteration() > 0) {
                    Assert.assertNotNull(d.haveError(Message.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                }
            }
            if ("test6".equals(d.methodInfo().name)) {
                if ("3".equals(d.statementId()) && d.iteration() > 0) {
                    Assert.assertNotNull(d.haveError(Message.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo system = typeMap.get(System.class);
            FieldInfo out = system.getFieldByName("out", true);
            Assert.assertEquals(Level.TRUE, out.fieldAnalysis.get().getProperty(VariableProperty.FINAL));

            TypeInfo string = typeMap.get(String.class);
            MethodInfo equals = string.findUniqueMethod("equals", 1);
            Assert.assertEquals(Level.FALSE, equals.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));

            MethodInfo toLowerCase = string.findUniqueMethod("toLowerCase", 0);
            Assert.assertFalse(toLowerCase.methodResolution.get().allowsInterrupts());

            TypeInfo printStream = typeMap.get(PrintStream.class);
            MethodInfo println = printStream.findUniqueMethod("println", 0);
            Assert.assertTrue(println.methodResolution.get().allowsInterrupts());
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("someMinorMethod".equals(d.methodInfo().name)) {
                Assert.assertFalse(d.methodInfo().methodResolution.get().allowsInterrupts());
            }
            if ("nonPrivateMethod".equals(d.methodInfo().name)) {
                Assert.assertTrue(d.methodInfo().methodResolution.get().allowsInterrupts());
            }
        };

        testClass("Basics_6", 0, 10, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    // more on statement time
    @Test
    public void test7() throws IOException {
        final String I = "org.e2immu.analyser.testexample.Basics_7.i";
        final String I0 = I + "$0";
        final String I1 = I + "$1";
        final String INC3_RETURN_VAR = "org.e2immu.analyser.testexample.Basics_7.increment3()";
        final String I_DELAYED = "<field:org.e2immu.analyser.testexample.Basics_7.i>";
        final String I_EXPR = "-1+" + I_DELAYED + "==" + I_DELAYED;

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("increment".equals(d.methodInfo().name) && "4".equals(d.statementId()) && d.iteration() > 0) {
                Assert.assertEquals("true", d.evaluationResult().value().toString());
            }
            if ("increment3".equals(d.methodInfo().name) && "1.0.3".equals(d.statementId()) && d.iteration() > 0) {
                Assert.assertEquals("true", d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Basics_7".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo p && "p".equals(p.name)) {
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("0.0.0" + VariableInfoContainer.Level.EVALUATION, d.variableInfo().getReadId());
                }
                if ("0".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfoContainer().hasMerge());
                    Assert.assertEquals("0" + VariableInfoContainer.Level.MERGE, d.variableInfo().getReadId());
                }
            }

            if ("increment".equals(d.methodInfo().name) && I.equals(d.variableName())) {
                if ("2".equals(d.statementId()) && d.iteration() > 0) {
                    Assert.assertEquals(I0 + "+q", d.currentValue().toString());
                }
            }
            if (INC3_RETURN_VAR.equals(d.variableName())) {
                if ("1.0.3".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? I_EXPR : "true";
                    Assert.assertEquals(expect, d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? I_EXPR : "true";
                    Assert.assertEquals(expect, d.currentValue().debugOutput());
                }
            }
            if ("increment3".equals(d.methodInfo().name) && "j".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? I_DELAYED : I1;
                    Assert.assertEquals(expect, d.currentValue().toString());
                }
            }
            if ("increment3".equals(d.methodInfo().name) && I0.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertTrue(d.iteration() > 0); // does not exist earlier!
                    Assert.assertEquals("instance type int", d.currentValue().toString());
                }
            }
            if ("increment3".equals(d.methodInfo().name) && I1.equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertTrue(d.iteration() > 0); // does not exist earlier!
                    Assert.assertEquals("instance type int", d.currentValue().toString());
                }
            }
            if ("increment3".equals(d.methodInfo().name) && I.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? I_DELAYED : "instance type int";
                    Assert.assertEquals(expect, d.currentValue().toString());
                    int expectStatementTime = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 0;
                    Assert.assertEquals(expectStatementTime, d.variableInfo().getStatementTime());
                    Assert.assertEquals("[0]", d.variableInfo().getReadAtStatementTimes().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? I_DELAYED : "instance type int";
                    Assert.assertEquals(expect, d.currentValue().toString());
                    int expectStatementTime = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 1;
                    Assert.assertEquals(expectStatementTime, d.variableInfo().getStatementTime());
                    Assert.assertEquals("[1]", d.variableInfo().getReadAtStatementTimes().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int time1 = d.statementAnalysis().statementTime(VariableInfoContainer.Level.INITIAL);
            int time3 = d.statementAnalysis().statementTime(VariableInfoContainer.Level.EVALUATION);
            int time4 = d.statementAnalysis().statementTime(VariableInfoContainer.Level.MERGE);

            // method itself is synchronised, so statement time stands still
            if ("increment".equals(d.methodInfo().name)) {
                Assert.assertEquals(0, time1);
                Assert.assertEquals(0, time3);
                Assert.assertEquals(0, time4);
            }
            if ("increment2".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals(0, time1);
                    Assert.assertEquals(1, time3);
                    Assert.assertEquals(1, time4);
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals(1, time1);
                    Assert.assertEquals(1, time3);
                    Assert.assertEquals(1, time4);
                }
            }
            if ("increment3".equals(d.methodInfo().name)) {
                if (d.statementId().startsWith("1.0")) {
                    Assert.assertEquals("true", d.state().toString());
                    Assert.assertEquals("true", d.condition().toString());
                    Assert.assertEquals("true", d.absoluteState().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("increment".equals(d.methodInfo().name)) {
                Assert.assertTrue(d.methodInfo().isSynchronized());
            }
            if ("increment3".equals(d.methodInfo().name)) {
                String expect = d.iteration() == 0 ? I_EXPR : "true";
                Assert.assertEquals(expect, d.methodAnalysis().getLastStatement()
                        .variables.get(INC3_RETURN_VAR).current().getValue().debugOutput());
                if (d.iteration() > 0) {
                    Assert.assertEquals("true", d.methodAnalysis().getSingleReturnValue().toString());
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
        final String I0 = TYPE + ".i$0";
        final String I1 = TYPE + ".i$1";
        final String I2 = TYPE + ".i$2";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test1".equals(d.methodInfo().name)) {
                if ("v".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertTrue(d.variableInfo().isAssigned());
                        Assert.assertEquals("l", d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        Assert.assertEquals("3" + VariableInfoContainer.Level.EVALUATION, d.variableInfo().getAssignmentId());
                        Assert.assertEquals("1+l", d.currentValue().toString());
                    }
                    if ("6".equals(d.statementId())) {
                        Assert.assertEquals("6" + VariableInfoContainer.Level.EVALUATION, d.variableInfo().getAssignmentId());
                        Assert.assertEquals("2+l", d.currentValue().toString());
                    }
                }
                if ("w".equals(d.variableName()) && "1".equals(d.statementId())) {
                    Assert.assertEquals("1+l", d.currentValue().toString());
                }
                if ("u".equals(d.variableName()) && "4".equals(d.statementId())) {
                    Assert.assertEquals("3+l", d.currentValue().toString());
                }
            }

            if ("test3".equals(d.methodInfo().name) && d.iteration() > 0) {
                if ("j".equals(d.variableName()) && "0".equals(d.statementId())) {
                    Assert.assertEquals(I0, d.currentValue().toString());
                }
                if (I.equals(d.variableName()) && "1".equals(d.statementId())) {
                    Assert.assertEquals(I0 + "+q", d.currentValue().toString());
                }
                if ("k".equals(d.variableName()) && "2".equals(d.statementId())) {
                    Assert.assertEquals(I0 + "+q", d.currentValue().toString());
                }
            }
            if ("test4".equals(d.methodInfo().name)) {
                if( "j".equals(d.variableName()) && ("1".equals(d.statementId())
                        || "2".equals(d.statementId())
                        || "3".equals(d.statementId())
                        || "4.0.0.0.0".equals(d.statementId()))) {
                    Assert.assertEquals("statement "+d.statementId(),
                            "this.i", d.variableInfo().getStaticallyAssignedVariables().toString());
                }

                if(d.iteration() > 0){
                    if ("j0".equals(d.variableName()) && "4.0.0.0.0".equals(d.statementId())) {
                        Assert.assertEquals(I2, d.currentValue().toString());
                    }
                    if (I.equals(d.variableName()) && "4.0.0.0.1".equals(d.statementId())) {
                        Assert.assertEquals(I2 + "+q", d.currentValue().toString());
                    }
                    if ("k0".equals(d.variableName()) && "4.0.0.0.2".equals(d.statementId())) {
                        Assert.assertEquals(I2 + "+q", d.currentValue().toString());
                    }
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test4".equals(d.methodInfo().name) && d.iteration() > 0 && "4.0.0.0.3".equals(d.statementId())) {
                Assert.assertEquals("true", d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("4.0.0.0.0".equals(d.statementId()) && d.iteration() > 0) {
                Assert.assertEquals(I1 + "==" + I2, d.absoluteState().toString());
            }
        };

        testClass("Basics_8", 0, 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }
}
