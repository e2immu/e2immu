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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_01_Loops_4 extends CommonTestRunner {

    public Test_01_Loops_4() {
        super(true);
    }

    @Test
    public void test_4() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "!<c:boolean>||!<loopIsNotEmptyCondition>" : "1!=instance type int";
                if ("0".equals(d.statementId())) {
                    assertEquals(expected, d.state().toString());
                    assertNull(d.haveError(Message.Label.INLINE_CONDITION_EVALUATES_TO_CONSTANT));

                    // state after a forEach only when there was an interrupt caused by "break"
                    assertEquals(expected, d.conditionManagerForNextStatement().state().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    assertEquals(expected, d.state().toString());
                    assertNull(d.haveError(Message.Label.INLINE_CONDITION_EVALUATES_TO_CONSTANT));
                }
            }

        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("i".equals(d.variableName())) {
                    if (d.statementId().startsWith("0")) {
                        if (d.variableInfoContainer().variableNature() instanceof VariableNature.LoopVariable loopVariable) {
                            assertEquals("0", loopVariable.statementIndex());
                        } else {
                            fail();
                        }
                    } else {
                        fail("Variable should not exist here: " + d.statementId());
                    }

                    if ("0.0.0.0.0".equals(d.statementId())) {
                        assertEquals("instance type int", d.currentValue().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("instance type int", d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        assertEquals("instance type int", d.currentValue().toString());
                        assertEquals("0-E", d.variableInfo().getReadId());
                        assertFalse(d.variableInfoContainer().hasMerge());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals("org.e2immu.analyser.parser.loops.testexample.Loops_4.method()", d.variableName());
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        // delayed state
                        assertEquals("4", d.currentValue().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<c:boolean>?4:<return value>" : "1==i?4:<return value>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasMerge());
                        // there should be no i here anymore!!
                        String expect = d.iteration() == 0 ? "<loopIsNotEmptyCondition>&&<c:boolean>?4:<return value>" :
                                "1==instance type int?4:<return value>";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        // we do not want 0 here!
                        String expect = d.iteration() == 0
                                ? "!<c:boolean>||!<loopIsNotEmptyCondition>?0:<c:boolean>&&<loopIsNotEmptyCondition>&&<c:boolean>&&<loopIsNotEmptyCondition>&&<c:boolean>?4:<return value>"
                                : "1==instance type int?4:0";
                        assertEquals(expect, d.currentValue().toString());
                        String expectVars = "[]";
                        assertEquals(expectVars, d.currentValue().variables(true).toString());
                    }
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expectValue = "i<=9";
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                }
                if ("0.0.0".equals(d.statementId())) {
                    String expectValue = "1==i";
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                }
            }
        };

        testClass("Loops_4", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
