
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

import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_03_Basics_4plus extends CommonTestRunner {

    public Test_03_Basics_4plus() {
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
                    String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() :
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
                int expect = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expect, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            }
        };
        final String TYPE = "org.e2immu.analyser.testexample.Basics_6";
        final String FIELD = TYPE + ".field";
        final String FIELD_0 = TYPE + ".field$0";
        final String FIELD_1 = TYPE + ".field$1";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test1".equals(d.methodInfo().name)) {
                if (FIELD.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        int expect = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 0;
                        Assert.assertEquals(expect, d.variableInfo().getStatementTime());
                    }
                    if ("2".equals(d.statementId())) {
                        int expect = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 1;
                        Assert.assertEquals(expect, d.variableInfo().getStatementTime());
                    }
                }
                if (FIELD_0.equals(d.variableName())) {
                    Assert.assertTrue(d.iteration() > 0);
                    Assert.assertEquals("instance type String", d.currentValue().toString());
                    Assert.assertEquals(VariableInfoContainer.NOT_A_VARIABLE_FIELD, d.variableInfo().getStatementTime());
                }
                if (FIELD_1.equals(d.variableName())) {
                    Assert.assertTrue(d.iteration() > 0);
                    Assert.assertEquals("instance type String", d.currentValue().toString());
                    Assert.assertEquals(VariableInfoContainer.NOT_A_VARIABLE_FIELD, d.variableInfo().getStatementTime());
                    Assert.assertTrue("Have " + d.statementId(), "2".compareTo(d.statementId()) <= 0);
                }
                if ("v1".equals(d.variableName()) && "0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : FIELD_0;
                    Assert.assertEquals(expect, d.currentValue().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int time1 = d.statementAnalysis().statementTime(1);
            int time3 = d.statementAnalysis().statementTime(3);
            int time4 = d.statementAnalysis().statementTime(4);

            if ("test1".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertEquals(3, d.statementAnalysis().variables.size());
                        Assert.assertEquals(0, time1);
                        Assert.assertEquals(0, time3);
                        Assert.assertEquals(0, time4);
                    }
                    if ("1".equals(d.statementId())) {
                        Assert.assertEquals(0, time1);
                        Assert.assertEquals(1, time3);
                        Assert.assertEquals(1, time4);
                        // 4 vars: field, this, v1, out
                        Assert.assertEquals(4, d.statementAnalysis().variables.size());
                    }
                    if ("2".equals(d.statementId())) {
                        Assert.assertEquals(1, time1);
                        Assert.assertEquals(1, time3);
                        Assert.assertEquals(1, time4);
                        // 5 vars: field, this, v1, v2, out
                        Assert.assertEquals(5, d.statementAnalysis().variables.size());
                    }
                    if ("3".equals(d.statementId())) {
                        Assert.assertEquals(1, time1);
                        Assert.assertEquals(2, time3);
                        Assert.assertEquals(2, time4);
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
            if ("test3".equals(d.methodInfo().name)) {
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
        };

        testClass("Basics_6", 0, 12, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }


    // more on statement time
    @Test
    public void test7() throws IOException {
        final String I = "org.e2immu.analyser.testexample.Basics_7.i";
        final String I0 = I + "$0";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("increment".equals(d.methodInfo().name) && "4".equals(d.statementId()) && d.iteration() > 0) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                Assert.assertEquals(I0 + "+q==" + I0, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("increment".equals(d.methodInfo().name) && I.equals(d.variableName())) {
                if ("2".equals(d.statementId()) && d.iteration() > 0) {
                    Assert.assertEquals(I0 + "+q", d.currentValue().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int time1 = d.statementAnalysis().statementTime(1);
            int time3 = d.statementAnalysis().statementTime(3);
            int time4 = d.statementAnalysis().statementTime(4);

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
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("increment".equals(d.methodInfo().name)) {
                Assert.assertTrue(d.methodInfo().isSynchronized());
            }
        };

        testClass("Basics_7", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    // assignment ids for local variables
    @Test
    public void test8() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test1".equals(d.methodInfo().name)) {
                if ("v".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.ASSIGNED));
                        Assert.assertEquals("l", d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        Assert.assertEquals(2, d.getProperty(VariableProperty.ASSIGNED));
                        Assert.assertEquals("1+l", d.currentValue().toString());
                    }
                    if ("6".equals(d.statementId())) {
                        Assert.assertEquals(3, d.getProperty(VariableProperty.ASSIGNED));
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
            final String TYPE = "org.e2immu.analyser.testexample.Basics_8";
            final String I = TYPE + ".i";
            final String I0 = TYPE + ".i$0";
            final String I2 = TYPE + ".i$2";

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
            if ("test4".equals(d.methodInfo().name) && d.iteration() > 0) {
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
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test4".equals(d.methodInfo().name) && d.iteration() > 0 && "4.0.0.0.3".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                Assert.assertEquals("true", d.evaluationResult().value().toString());
            }
        };

        testClass("Basics_8", 0, 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }
}
