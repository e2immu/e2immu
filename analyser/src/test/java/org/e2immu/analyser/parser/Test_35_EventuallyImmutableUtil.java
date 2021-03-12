
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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class Test_35_EventuallyImmutableUtil extends CommonTestRunner {

    private static final List<String> FLIP_SWITCH_SET_ONCE = List.of("FlipSwitch", "SetOnce");

    public Test_35_EventuallyImmutableUtil() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testWithUtilClasses(List.of("EventuallyImmutableUtil_0"), List.of("FlipSwitch"),
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_1() throws IOException {
        testWithUtilClasses(List.of("EventuallyImmutableUtil_1"), FLIP_SWITCH_SET_ONCE,
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_2() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("set".equals(d.methodInfo().name) && "EventuallyImmutableUtil_2".equals(d.methodInfo().typeInfo.simpleName)) {
                String expectPre = switch (d.iteration()) {
                    case 0 -> "<precondition>";
                    case 1 -> "null==<f:t>";
                    default -> "null==value.t";
                };
                Assert.assertEquals(expectPre, d.statementAnalysis().stateData.getPrecondition().toString());
                Assert.assertEquals(d.iteration() <= 1, d.statementAnalysis().stateData.preconditionIsDelayed());
            }
        };
        testWithUtilClasses(List.of("EventuallyImmutableUtil_2"), FLIP_SWITCH_SET_ONCE,
                0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .build());
    }

    @Test
    public void test_3() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("isReady".equals(d.methodInfo().name)) {
                // preconditions have nothing to do with this
                Assert.assertEquals("true", d.statementAnalysis().stateData.getPrecondition().toString());
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("isReady".equals(d.methodInfo().name) && d.variable() instanceof ReturnVariable) {
                String expectValue = d.iteration() == 0 ? "<m:isSet>&&<m:isSet>" : "bool.isSet()&&string.isSet()";
                Assert.assertEquals(expectValue, d.currentValue().toString());
            }
        };

        testWithUtilClasses(List.of("EventuallyImmutableUtil_3"), FLIP_SWITCH_SET_ONCE,
                0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build());
    }

    @Test
    public void test_4() throws IOException {
        testWithUtilClasses(List.of("EventuallyImmutableUtil_4"), FLIP_SWITCH_SET_ONCE,
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_5() throws IOException {
        testWithUtilClasses(List.of("EventuallyImmutableUtil_5"), FLIP_SWITCH_SET_ONCE,
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_6() throws IOException {
        testWithUtilClasses(List.of("EventuallyImmutableUtil_6"), List.of("AddOnceSet"),
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }
}
