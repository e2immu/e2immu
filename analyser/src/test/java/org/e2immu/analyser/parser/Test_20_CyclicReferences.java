
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
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_20_CyclicReferences extends CommonTestRunner {
    public Test_20_CyclicReferences() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("CyclicReferences_0", 0, 0, new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("findTailRecursion".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                assertEquals("!list.get(0).equals(find)&&!list.isEmpty()&&CyclicReferences_1.findTailRecursion(find,list.subList(1,list.size()))",
                        d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("findTailRecursion".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo p && p.name.equals("list")) {
                assertEquals("nullable instance type List<String>", d.currentValue().toString());
                assertEquals("", d.variableInfo().getLinkedVariables().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("findTailRecursion".equals(d.methodInfo().name)) {
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };

        testClass("CyclicReferences_1", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("methodB".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<m:methodA>" : "\"a\".equals(paramB)&&!\"b\".equals(paramB)";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<m:equals>&&!<m:equals>" : "!\"a\".equals(paramB)&&\"b\".equals(paramB)";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("methodB".equals(d.methodInfo().name) || "methodA".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().methodResolution.get().methodsOfOwnClassReached().contains(d.methodInfo()));
            }
        };

        testClass("CyclicReferences_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("CyclicReferences_3", 0, 0, new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_4() throws IOException {
        testClass("CyclicReferences_4", 0, 0, new DebugConfiguration.Builder().build());
    }
}
