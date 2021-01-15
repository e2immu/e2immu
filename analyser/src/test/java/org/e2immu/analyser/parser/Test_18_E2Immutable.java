/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_18_E2Immutable extends CommonTestRunner {
    public Test_18_E2Immutable() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("E2Immutable_0", 0, 0, new DebugConfiguration.Builder()

                .build());
    }

    @Test
    public void test_1() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.E2Immutable_1";
        final String CONSTRUCTOR2 = TYPE + ".E2Immutable_1(E2Immutable_1,String)";
        final String LEVEL2 = TYPE + ".level2";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (CONSTRUCTOR2.equals(d.methodInfo().fullyQualifiedName) && LEVEL2.equals(d.variableName())) {
                if ("1".equals(d.statementId())) {
                    // we never know in the first iteration...
                    String expectValue = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "2+parent2Param.level2";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                }
            }
        };
        testClass("E2Immutable_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("E2Immutable_2", 0, 0, new DebugConfiguration.Builder()

                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("E2Immutable_3", 0, 0, new DebugConfiguration.Builder()

                .build());
    }

    @Test
    public void test_4() throws IOException {
        testClass("E2Immutable_4", 0, 0, new DebugConfiguration.Builder()

                .build());
    }

    @Test
    public void test_5() throws IOException {
        testClass("E2Immutable_5", 0, 0, new DebugConfiguration.Builder()

                .build());
    }

    @Test
    public void test_6() throws IOException {
        testClass("E2Immutable_6", 0, 0, new DebugConfiguration.Builder()

                .build());
    }

    @Test
    public void test_7() throws IOException {
        testClass("E2Immutable_7", 0, 0, new DebugConfiguration.Builder()

                .build());
    }

    @Test
    public void test_8() throws IOException {
        testClass("E2Immutable_8", 0, 0, new DebugConfiguration.Builder()

                .build());
    }
}
