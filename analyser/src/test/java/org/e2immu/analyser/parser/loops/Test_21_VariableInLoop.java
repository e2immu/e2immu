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

package org.e2immu.analyser.parser.loops;

import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_21_VariableInLoop extends CommonTestRunner {

    public Test_21_VariableInLoop() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("found".equals(d.variableName())) {
                    if ("1.0.0.0.0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop);
                        VariableInfo vi1 = d.variableInfoContainer().getPreviousOrInitial();
                        String expected = d.iteration() == 0 ? "<vl:found>" : "instance type boolean";
                        assertEquals(expected, vi1.getValue().toString());

                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(expected, eval.getValue().toString());
                        String expectMerge = d.iteration() == 0 ? "<simplification>" : "instance type boolean||!found$1";
                        assertEquals(expectMerge, d.currentValue().toString()); // result of merge, should always be "true"
                    }
                    if ("1.0.0.0.0.0.0".equals(d.statementId())) {
                        VariableInfo vi1 = d.variableInfoContainer().getPreviousOrInitial();
                        String expected = d.iteration() == 0 ? "<vl:found>" : "instance type boolean";
                        assertEquals(expected, vi1.getValue().toString());
                        assertEquals("true", d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0.0.0.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "!<v:found>" : "!found$1";
                    assertEquals(expected, d.condition().toString());
                }
            }
        };
        testClass("VariableInLoop_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("findFirstStatementWithDelays".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<null-check>&&(<null-check>||!<m:isPresent>)?<v:sa>:<return value>"
                                : "(sa$1.navigationData().next.isPresent()||null==sa$1)&&(null==sa$1||null!=sa$1.navigationData().next.get().orElse(null))?<return value>:sa$1";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("sa".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<null-check>?<m:get>:<vl:sa>" : "null";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        // FIXME this error is problematic, needs solving!!
        testClass("VariableInLoop_1", 1, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
