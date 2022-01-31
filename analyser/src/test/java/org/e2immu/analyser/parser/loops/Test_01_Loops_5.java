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
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_01_Loops_5 extends CommonTestRunner {

    public Test_01_Loops_5() {
        super(true);
    }

    @Test
    public void test_5() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    // if(i==1)...
                    String expect = d.iteration() == 0 ? "1==<v:i>" : "1==i$1";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("2".equals(d.statementId())) {
                    // assert i >= 10, with i == instance type int
                    String expect = d.iteration() == 0 ? "<v:i>>=10" : "i>=10";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                if ("i".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.NormalLocalVariable);
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        if (d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop v) {
                            assertEquals("1", v.statementIndex());
                        } else fail();
                        String expect = d.iteration() == 0 ? "<v:i>" : "instance type int";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasMerge());
                        String expect = d.iteration() == 0 ? "<v:i>" : "instance type int";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        // this is SAHelper.copyFromStateIntoValue in action
                        String expect = d.iteration() == 0 ? "<v:i>" : "10";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable && "2".equals(d.statementId())) {
                    String expectReturn = d.iteration() == 0 ? "<loopIsNotEmptyCondition>&&1==<v:i>?5:<return value>" :
                            "1==i$1?5:<return value>";
                    assertEquals(expectReturn, d.currentValue().toString());
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if ("i".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        // in the current implementation, the state is  "1==i||10==i" (see below).
                        // so we cannot yet deduce that "i" must be 1
                        String expect = d.iteration() == 0 ? "<v:i>" : "instance type int";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        // however, after the assert statement, we must conclude that i==1,
                        // again  SAHelper.copyFromStateIntoValue in action
                        String expect = d.iteration() == 0 ? "<v:i>" : "1";
//FIXME                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertRange(d, "NumericRange[startIncl=0, endExcl=10, increment=1, variableExpression=i$1]", "i$1<=9&&i$1>=0");

                    String expectState = d.iteration() == 0 ? "<s:boolean>&&(!<loopIsNotEmptyCondition>||1!=<v:i>)" : "10==i";
                    assertEquals(expectState, d.state().toString());
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expect = d.iteration() == 0
                            ? "(<s:boolean>||<loopIsNotEmptyCondition>)&&(<s:boolean>||1==<v:i>)" : "1==i||10==i";
                    assertEquals(expect, d.state().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "1==<v:i>" : "1==i";
       //FIXME             assertEquals(expect, d.state().toString());
                }
            }
        };
        // expect: warning: always true in assert
        testClass("Loops_5", 0, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }
}
