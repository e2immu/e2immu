
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
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.EvaluationResult;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class Test_16_BasicCompanionMethods extends CommonTestRunner {

    public static final String LIST_SIZE = "instance type java.util.ArrayList()[0 == java.util.ArrayList.this.size()]";

    public Test_16_BasicCompanionMethods() {
        super(true);
    }

    @Test
    public void test0() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("0".equals(d.statementId())) {
                Assert.assertEquals("<empty>", d.state().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "list".equals(d.variableName())) {
                Assert.assertEquals(LIST_SIZE, d.currentValue().toString());
                Assert.assertEquals(LIST_SIZE, d.variableInfo().getInstance().toString());
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                EvaluationResult.ValueChangeData valueChangeData = d.evaluationResult().getValueChangeStream()
                        .filter(e -> "list".equals(e.getKey().fullyQualifiedName())).map(Map.Entry::getValue).findFirst().orElseThrow();
                Assert.assertEquals(LIST_SIZE, valueChangeData.instance().toString());
            }
            if ("test".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                Assert.assertEquals("false", d.evaluationResult().value.toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                Assert.assertEquals("4", d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        TypeContextVisitor typeContextVisitor = typeContext -> {
            TypeInfo charSequence = typeContext.getFullyQualified(CharSequence.class);
            MethodInfo length = charSequence.findUniqueMethod("length", 0);
            int modified = length.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
            Assert.assertEquals(Level.FALSE, modified);
        };

        // two errors: two unused parameters
        testClass("BasicCompanionMethods_0", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }


    @Test
    public void test1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                EvaluationResult.ValueChangeData valueChangeData = d.evaluationResult().getValueChangeStream()
                        .filter(e -> "list".equals(e.getKey().fullyQualifiedName())).map(Map.Entry::getValue).findFirst().orElseThrow();
                Assert.assertEquals(LIST_SIZE, valueChangeData.instance().toString());
            }
            if ("test".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                Assert.assertTrue(d.haveValueChange("list"));
                Assert.assertEquals("instance type java.util.ArrayList()[1 == java.util.List.this.size()]", d.findValueChange("list").instance().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "1".equals(d.statementId()) && "list".equals(d.variableName())) {
                Assert.assertEquals("instance type java.util.ArrayList()[(1 == java.util.ArrayList.this.size()) and (java.util.ArrayList.this.contains(\"a\")]",
                        d.variableInfo().getInstance().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                Assert.assertEquals("true", d.methodAnalysis().getSingleReturnValue().toString());
            }

        };
        testClass("BasicCompanionMethods_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test2() throws IOException {


        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "1".equals(d.statementId()) && "list".equals(d.variableName())) {
                Assert.assertEquals("instance type java.util.ArrayList()[(1 == java.util.ArrayList.this.size()) and (java.util.ArrayList.this.contains(\"a\")]",
                        d.currentValue().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                Assert.assertEquals("true", d.methodAnalysis().getSingleReturnValue().toString());
            }

        };
        testClass("BasicCompanionMethods_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "0".equals(d.statementId()) && "sb".equals(d.variableName())) {
                Assert.assertEquals("", d.currentValue().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                Assert.assertEquals("true", d.methodAnalysis().getSingleReturnValue().toString());
            }

        };
        testClass("BasicCompanionMethods_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
