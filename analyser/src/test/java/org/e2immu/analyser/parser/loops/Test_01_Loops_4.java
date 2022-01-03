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
                if ("0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<replace:int>>=10" : "instance type int>=10";
                    assertEquals(expect, d.state().toString());
                    assertNull(d.haveError(Message.Label.INLINE_CONDITION_EVALUATES_TO_CONSTANT));
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    String expectState = d.iteration() == 0 ? "<replace:int>>=10" : "instance type int>=10";
                    assertEquals(expectState, d.state().toString());
                    assertNull(d.haveError(Message.Label.INLINE_CONDITION_EVALUATES_TO_CONSTANT));
                }
            }

        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("i".equals(d.variableName())) {
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.LoopVariable loopVariable) {
                        assertEquals("0", loopVariable.statementIndex());
                    } else {
                        fail();
                    }
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$0";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$0";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$0";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals("org.e2immu.analyser.testexample.Loops_4.method()", d.variableName());
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        // delayed state
                        String expect = "4";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1==<v:i>?4:<return value>" : "0==i$0?4:<return value>";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<replace:int><=9?<merge:int>:<return value>"
                                : "instance type int<=9?instance type int:<return value>";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() == 0
                                ? "<replace:int>>=10?0:<replace:int><=9&&<replace:int><=9?<merge:int>:<return value>"
                                : "instance type int>=10?0:instance type int";
                        assertEquals(expect, d.currentValue().toString());
                        String expectVars = d.iteration() == 0 ? "[method]" : "[]";
                        assertEquals(expectVars, d.currentValue().variables().toString());
                    }
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<v:i><=9" : "i$0<=9";
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                }
                if ("0.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "1==<v:i>" : "0==i$0";
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
