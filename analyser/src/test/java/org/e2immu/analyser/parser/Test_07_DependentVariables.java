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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_07_DependentVariables extends CommonTestRunner {
    public Test_07_DependentVariables() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        final String A2 = "org.e2immu.analyser.testexample.DependentVariables.method2(int):0:a";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {

                String read = d.variableInfo().getReadId();
                String assigned = d.variableInfo().getAssignmentIds().getLatestAssignment();

                if ("1".equals(d.statementId()) && "array[0]".equals(d.variableName())) {
                    assertEquals("12", d.currentValue().toString());
                }
                if ("2".equals(d.statementId()) && "array[0]".equals(d.variableName())) {
                    assertTrue(assigned.compareTo(read) > 0);
                    assertEquals("12", d.variableInfo().getValue().toString());
                }
                if ("2".equals(d.statementId()) && "array[1]".equals(d.variableName())) {
                    assertEquals("13", d.currentValue().toString());
                }
                if ("4".equals(d.statementId()) && "array[0]".equals(d.variableName())) {
                    assertTrue(assigned.compareTo(read) < 0);
                    assertEquals("12", d.variableInfo().getValue().toString());
                }
                if ("4".equals(d.statementId()) && "array[1]".equals(d.variableName())) {
                    assertTrue(assigned.compareTo(read) < 0);
                    assertEquals("13", d.variableInfo().getValue().toString());
                }
                if ("4".equals(d.statementId()) && "array[2]".equals(d.variableName())) {
                    assertTrue(assigned.compareTo(read) < 0);
                    assertEquals("31", d.variableInfo().getValue().toString());
                }
                if ("4".equals(d.statementId()) && "array".equals(d.variableName())) {
                    assertEquals("4" + VariableInfoContainer.Level.EVALUATION, read);
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if (A2.equals(d.variableName())) {
                    assertEquals("instance type int/*@Identity*/", d.currentValue().toString());
                }
                if ("b".equals(d.variableName())) {
                    assertEquals("a", d.variableInfo().getValue().toString());
                }
                if ("3".equals(d.statementId())) {
                    if ("array[a]".equals(d.variableName())) {
                        fail("This variable should not be produced");
                    }
                    if ("array[b]".equals(d.variableName())) {
                        fail("This variable should not be produced");
                    }
                    if (("array[" + A2 + "]").equals(d.variableName())) {
                        assertEquals("12", d.variableInfo().getValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name) && "4".equals(d.statementId())) {
                VariableInfo tv = d.getReturnAsVariable();
                assertEquals("56", tv.getValue().toString());
            }
            if ("method2".equals(d.methodInfo().name) && "3".equals(d.statementId())) {
                VariableInfo tv = d.getReturnAsVariable();
                assertEquals("12", tv.getValue().toString());
            }
        };

        // unused parameter in method1
        testClass("DependentVariables_0", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("DependentVariables_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
