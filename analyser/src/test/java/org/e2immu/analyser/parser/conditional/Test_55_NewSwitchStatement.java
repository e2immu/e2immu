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

package org.e2immu.analyser.parser.conditional;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.BreakDelayVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_55_NewSwitchStatement extends CommonTestRunner {
    public Test_55_NewSwitchStatement() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        testClass("NewSwitchStatement_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("NewSwitchStatement_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("NewSwitchStatement_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("s".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("<not yet assigned>", d.currentValue().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("\"x\"", d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("instance 1 type String", d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d-> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("dataType==3", d.condition().toString());
                }
                if ("1.1.0".equals(d.statementId())) {
                    assertEquals("dataType==4", d.condition().toString());
                }
                if ("1.2.0".equals(d.statementId())) {
                    assertEquals("!(dataType==3)&&!(dataType==4)", d.condition().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                String expected = "/*inline method*/s$1";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("--", d.delaySequence());

        testClass("NewSwitchStatement_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }
}
