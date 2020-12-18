
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

import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_03_Basics_4_5 extends CommonTestRunner {

    public Test_03_Basics_4_5() {
        super(true);
    }

    // i = i + 1 on a field
    @Test
    public void test4() throws IOException {
        testClass("Basics_4", 0, 0, new DebugConfiguration.Builder()

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
                if ("v1".equals(d.variableName()) && "0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "field";
                    Assert.assertEquals(expect, d.currentValue().toString());
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
                    Assert.assertTrue("Have "+d.statementId(), "2".compareTo(d.statementId()) <= 0);
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
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo system = typeMap.get(System.class);
            FieldInfo out = system.getFieldByName("out", true);
            Assert.assertEquals(Level.TRUE, out.fieldAnalysis.get().getProperty(VariableProperty.FINAL));
        };

        testClass("Basics_6", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

}
