
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
import org.e2immu.analyser.config.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_00_Size extends CommonTestRunner {

    private static final String TYPE = "org.e2immu.analyser.testexample.Size";

    public Test_00_Size() {
        super(true);
    }

    @Test
    public void test0() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                Assert.assertEquals("<empty>", d.state().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "0".equals(d.statementId()) && "list".equals(d.variableName())) {
                Assert.assertEquals("instance type java.util.ArrayList()[0 == java.util.ArrayList.this.size()]", d.currentValue().toString());
            }
            if ("test2".equals(d.methodInfo().name) && "1".equals(d.statementId()) && "list".equals(d.variableName())) {
                Assert.assertEquals("instance type java.util.ArrayList()[(1 == java.util.ArrayList.this.size()) and (java.util.ArrayList.this.contains(\"a\")]",
                        d.currentValue().toString());
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                // ((-1) + instance type java.util.ArrayList()[0 == java.util.ArrayList.this.size()].size()) >= 0 needs to reduce...

                Assert.assertEquals("false", d.evaluationResult().value.toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                Assert.assertEquals("4", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("test2".equals(d.methodInfo().name) || "test3".equals(d.methodInfo().name)) {
                Assert.assertEquals("true", d.methodAnalysis().getSingleReturnValue().toString());
            }

        };
        // two errors: two unused parameters
        testClass("Size_0", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test1() throws IOException {
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
        testClass("Size_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
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
        testClass("Size_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
