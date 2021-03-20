
/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_35_EventuallyImmutableUtil extends CommonTestRunner {

    private static final List<String> FLIP_SWITCH_SET_ONCE = List.of("FlipSwitch", "SetOnce");

    public Test_35_EventuallyImmutableUtil() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testSupportAndUtilClasses(List.of("EventuallyImmutableUtil_0"), List.of("FlipSwitch"), ORG_E2IMMU_SUPPORT,
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_1() throws IOException {
        testSupportAndUtilClasses(List.of("EventuallyImmutableUtil_1"), FLIP_SWITCH_SET_ONCE, ORG_E2IMMU_SUPPORT,
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
                assertEquals(expectPre, d.statementAnalysis().stateData.precondition.get().toString());
                assertEquals(d.iteration() <= 1, d.statementAnalysis().stateData.precondition.isVariable());
            }
        };
        testSupportAndUtilClasses(List.of("EventuallyImmutableUtil_2"), FLIP_SWITCH_SET_ONCE, ORG_E2IMMU_SUPPORT,
                0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .build());
    }

    @Test
    public void test_3() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("isReady".equals(d.methodInfo().name)) {
                // preconditions have nothing to do with this
                assertEquals("true", d.statementAnalysis().stateData.precondition.get().toString());
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("isReady".equals(d.methodInfo().name) && d.variable() instanceof ReturnVariable) {
                String expectValue = d.iteration() == 0 ? "<m:isSet>&&<m:isSet>" : "bool.isSet()&&string.isSet()";
                assertEquals(expectValue, d.currentValue().toString());
            }
        };

        testSupportAndUtilClasses(List.of("EventuallyImmutableUtil_3"), FLIP_SWITCH_SET_ONCE, ORG_E2IMMU_SUPPORT,
                0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build());
    }

    @Test
    public void test_4() throws IOException {
        testSupportAndUtilClasses(List.of("EventuallyImmutableUtil_4"), FLIP_SWITCH_SET_ONCE, ORG_E2IMMU_SUPPORT,
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_5() throws IOException {
        testSupportAndUtilClasses(List.of("EventuallyImmutableUtil_5"), FLIP_SWITCH_SET_ONCE, ORG_E2IMMU_SUPPORT,
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_6() throws IOException {
        testSupportAndUtilClasses(List.of("EventuallyImmutableUtil_6"), List.of("AddOnceSet"), ORG_E2IMMU_SUPPORT,
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_7() throws IOException {
        testSupportAndUtilClasses(List.of("EventuallyImmutableUtil_7"), List.of("Freezable"), ORG_E2IMMU_SUPPORT,
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_8() throws IOException {
        testSupportAndUtilClasses(List.of("EventuallyImmutableUtil_8"), List.of("Freezable"), ORG_E2IMMU_SUPPORT,
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_9() throws IOException {
        testSupportAndUtilClasses(List.of("EventuallyImmutableUtil_9"), List.of("Freezable"), ORG_E2IMMU_SUPPORT,
                0, 0, new DebugConfiguration.Builder()
                        .build());
    }
}
