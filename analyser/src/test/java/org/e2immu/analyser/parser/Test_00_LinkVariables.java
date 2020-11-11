
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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Test_00_LinkVariables extends CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(Test_00_LinkVariables.class);

    private static final String TYPE = "org.e2immu.analyser.testexample.LinkVariables";
    private static final String P0 = TYPE + ".LinkVariables(Set<String>,Set<String>,String):0:p0";
    private static final String FIELD1 = TYPE + ".f1";
    private static final String GET_F1_RETURN = TYPE + ".getF1()";
    public static final String LINK_VARIABLES = "LinkVariables";

    public Test_00_LinkVariables() {
        super(true);
    }


    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if (LINK_VARIABLES.equals(d.methodInfo().name) && "0".equals(d.statementId())) {
            if ("s1".equals(d.variableName())) {
                Assert.assertEquals("[" + P0 + "]", d.variableInfo().getLinkedVariables().toString());
            }
        }
        if (LINK_VARIABLES.equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            if (FIELD1.equals(d.variableName())) {
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.ASSIGNED));
                Assert.assertEquals("[" + P0 + "]", d.variableInfo().getLinkedVariables().toString());
            }
        }
        if ("getF1".equals(d.methodInfo().name)) {
            if (FIELD1.equals(d.variableName())) {
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.READ));
                Assert.assertEquals("[]", d.variableInfo().getLinkedVariables().toString());
            }
            if (GET_F1_RETURN.equals(d.variableName())) {
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.ASSIGNED));
                if (d.iteration() > 0) {
                    Assert.assertEquals("[" + FIELD1 + "]", d.variableInfo().getLinkedVariables().toString());
                }
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if (LINK_VARIABLES.equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            Assert.assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
        }
        if ("getF1".equals(d.methodInfo().name) && d.iteration() > 0) {
            Assert.assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("f1".equals(d.fieldInfo().name)) {
            if (d.iteration() > 0) {
                Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                Assert.assertEquals(FIELD1, d.fieldAnalysis().getEffectivelyFinalValue().toString());
                Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED));
                Assert.assertEquals(Level.IS_A_SIZE, d.fieldAnalysis().getProperty(VariableProperty.SIZE));
            }
            if (d.iteration() > 1) {
                Assert.assertEquals("[" + P0 + "]", d.fieldAnalysis().getVariablesLinkedToMe().toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if (LINK_VARIABLES.equals(d.methodInfo().name)) {
            ParameterAnalysis p0 = d.parameterAnalyses().get(0);
            Assert.assertEquals(Level.FALSE, p0.getProperty(VariableProperty.MODIFIED));
        }
        if ("getF1".equals(d.methodInfo().name) && d.iteration() > 0) {
            Assert.assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
        }
    };

    @Test
    public void test() throws IOException {
        // two errors: two unused parameters
        testClass(LINK_VARIABLES, 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
