
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
import org.e2immu.analyser.model.ParameterAnalysis;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_00_Basics_1 extends CommonTestRunner {

    public static final String BASICS_1 = "Basics_1";
    private static final String TYPE = "org.e2immu.analyser.testexample." + BASICS_1;
    private static final String FIELD1 = TYPE + ".f1";
    private static final String GET_F1_RETURN = TYPE + ".getF1()";

    public Test_00_Basics_1() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if (BASICS_1.equals(d.methodInfo().name) && "0".equals(d.statementId())) {
            if ("s1".equals(d.variableName())) {
                Assert.assertEquals("p0", d.variableInfo().getLinkedVariables().toString());
            }
        }
        if (BASICS_1.equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            if (FIELD1.equals(d.variableName())) {
                Assert.assertTrue(d.variableInfo().isAssigned());
                Assert.assertEquals("p0", d.variableInfo().getLinkedVariables().toString());
            }
        }
        if ("getF1".equals(d.methodInfo().name)) {
            if (FIELD1.equals(d.variableName())) {
                Assert.assertTrue(d.variableInfo().isRead());
                if (d.iteration() == 0) {
                    Assert.assertSame(LinkedVariables.DELAY, d.variableInfo().getLinkedVariables());
                } else {
                    Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                String expectValue = d.iteration() == 0 ? "xx" : "nullable? instance type Set<String>";
                Assert.assertEquals(expectValue, d.currentValue().toString());
                int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
              //  Assert.assertEquals(expectNN, d.getProperty(VariableProperty.NOT_NULL));
            }
            if (GET_F1_RETURN.equals(d.variableName())) {
                Assert.assertTrue(d.variableInfo().isAssigned());
                if (d.iteration() == 0) {
                    Assert.assertSame(LinkedVariables.DELAY, d.variableInfo().getLinkedVariables());
                } else {
                    Assert.assertEquals("this.f1", d.variableInfo().getLinkedVariables().toString()); // without p0
                }
                String expectValue = d.iteration() == 0 ? "xx" : "f1";
                Assert.assertEquals(expectValue, d.currentValue().toString());
                int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
             //   Assert.assertEquals(expectNN, d.getProperty(VariableProperty.NOT_NULL));
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if (BASICS_1.equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            Assert.assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
        }
        if ("getF1".equals(d.methodInfo().name) && d.iteration() > 1) {
            Assert.assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("f1".equals(d.fieldInfo().name)) {
            Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            Assert.assertEquals("p0", d.fieldAnalysis().getEffectivelyFinalValue().debugOutput());
            Assert.assertEquals("p0", d.fieldAnalysis().getLinkedVariables().toString());
            if (d.iteration() == 0) {
                Assert.assertEquals(Level.DELAY, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            } else {
                Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
            int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            Assert.assertEquals(expectNN, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if (BASICS_1.equals(d.methodInfo().name)) {
            ParameterAnalysis p0 = d.parameterAnalyses().get(0);
            Assert.assertEquals(Level.FALSE, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
            int expectNN = d.iteration() <= 1 ? Level.DELAY : MultiLevel.NULLABLE;
            Assert.assertEquals(expectNN, p0.getProperty(VariableProperty.NOT_NULL_VARIABLE));
        }
        if ("getF1".equals(d.methodInfo().name)) {
            int expectMod = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
            Assert.assertEquals(expectMod, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            Assert.assertEquals(expectNN, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        }
    };

    @Test
    public void test() throws IOException {
        // two warnings: two unused parameters
        testClass(BASICS_1, 0, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
