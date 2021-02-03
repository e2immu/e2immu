
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
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_26_Enum extends CommonTestRunner {

    public Test_26_Enum() {
        super(false);
    }

    @Test
    public void test0() throws IOException {
        testClass("Enum_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test1() throws IOException {
        final String RET_VAR = "org.e2immu.analyser.testexample.Enum_1.posInList()";
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("posInList".equals(d.methodInfo().name) && RET_VAR.equals(d.variableName())) {
                if ("0.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() <= 1 ? "xx" :
                            "instance type Enum_1==this?1+i$0:<return value>";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                }
                if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                    String expectValue = d.iteration() <= 1 ? "xx" :
                            "instance type int<=2?instance type Enum_1==this?1+instance type int:<return value>:<return value>";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                }
            }
        };

        testClass("Enum_1", 0, 0, new DebugConfiguration.Builder()
                //  .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test2() throws IOException {
        testClass("Enum_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test3() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Enum_3";
        final String ONE = TYPE + ".THREE";
        final String TWO = TYPE + ".TWO";
        final String THREE = TYPE + ".THREE";
        final String THIS = TYPE + ".this";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("posInList".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<array length>><variable:i>" :
                        d.iteration() == 1 ? "<array length>>i$2" : "i$2<=2";
                Assert.assertEquals(expectValue, d.evaluationResult().value().toString());
            }
            if ("posInList".equals(d.methodInfo().name) && "2.0.0".equals(d.statementId())) {
                String expectValue = "nullable? instance type Enum_3==this";
                Assert.assertEquals(expectValue, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"posInList".equals(d.methodInfo().name)) return;
            if ("array".equals(d.variableName()) && ("0".equals(d.statementId()) || "1".equals(d.statementId()))) {
                String expectValue = d.iteration() <= 2 ? "<method:org.e2immu.analyser.testexample.Enum_3.values()>" : "{ONE,TWO,THREE}";
                Assert.assertEquals(expectValue, d.currentValue().toString());
            }
            if ("array[i]".equals(d.variableName())) {
                String expectValue = d.iteration() <= 2 ? "<state>" : "instance type Enum_3";
                Assert.assertEquals(expectValue, d.currentValue().toString());
            }
            if (THIS.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("instance type Enum_3", d.currentValue().toString());
                }
            }
            if (THREE.equals(d.variableName())) {
                if ("0".equals(d.statementId()) || "1".equals(d.statementId()) || "2".equals(d.statementId())) {
                    Assert.assertEquals("new Enum_3(3)", d.currentValue().toString());
                    Assert.assertTrue(d.iteration() >= 3);
                }
                // FIXME not visible in 2.0.0, 2.0.0.0.0
            }
            if ("i$2".equals(d.variableName())) {
                String expectValue = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "i";
                Assert.assertEquals("Statement " + d.statementId() + ", it " + d.iteration(),
                        expectValue, d.variableInfo().getLinkedVariables().toString());
            }
            if ("i$2$2-E".equals(d.variableName())) {
                String expectValue = d.iteration() <= 2 ? "<variable:i$2$2-E>" : "1+i$2";
                Assert.assertEquals("Statement " + d.statementId() + ", it " + d.iteration(),
                        expectValue, d.currentValue().toString());
                String expectLinked = d.iteration() <= 2 ? LinkedVariables.DELAY_STRING : "i";
                Assert.assertEquals("Statement " + d.statementId() + ", it " + d.iteration(),
                        expectLinked, d.variableInfo().getLinkedVariables().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("posInList".equals(d.methodInfo().name)) { // starting from statement 0, they'll all have to be there
                Assert.assertEquals(d.iteration() > 1, d.statementAnalysis().variables.isSet(ONE));
                Assert.assertEquals(d.iteration() > 1, d.statementAnalysis().variables.isSet(TWO));
                Assert.assertEquals(d.iteration() > 1, d.statementAnalysis().variables.isSet(THREE));

                if ("2.0.0.0.0".equals(d.statementId())) {
                    String expectCondition = "nullable? instance type Enum_3==this";
                    Assert.assertEquals(expectCondition, d.condition().toString());
                }

                if ("2.0.0".equals(d.statementId())) {
                    Assert.assertTrue(d.statementAnalysis().variables.isSet("array[i]"));
                    String expectCondition = d.iteration() == 0 ? "<array length>><variable:i>" :
                            d.iteration() == 1 ? "<array length>>i$2" : "i$2<=2";
                    Assert.assertEquals(expectCondition, d.condition().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("highest".equals(d.methodInfo().name)) {
                int expectConstant = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectConstant, d.methodAnalysis().getProperty(VariableProperty.CONSTANT));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("THREE".equals(d.fieldInfo().name)) {
                int expectConstant = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectConstant, d.fieldAnalysis().getProperty(VariableProperty.CONSTANT));
            }
            if ("cnt".equals(d.fieldInfo().name)) {
                Assert.assertEquals("cnt", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED));
            }
        };


        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            int expectImmutable = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            Assert.assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
        };

        // expect an "always true" warning on the assert
        testClass("Enum_3", 0, 1, new DebugConfiguration.Builder()
                //  .addEvaluationResultVisitor(evaluationResultVisitor)
                //  .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                //     .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test4() throws IOException {
        testClass("Enum_4", 0, 2, new DebugConfiguration.Builder()
                .build());
    }
}
