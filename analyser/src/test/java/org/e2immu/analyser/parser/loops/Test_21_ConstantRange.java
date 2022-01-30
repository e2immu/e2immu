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
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_21_ConstantRange extends CommonTestRunner {

    public Test_21_ConstantRange() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<return value>||<m:equals>"
                                : "<return value>||s.equals(\"c\")";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "input.length>0?<return value>||<m:equals>:<return value>"
                                : "input.length>0?<return value>||(instance type String).equals(\"c\"):<return value>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:equals>&&input.length>0"
                                : "(instance type String).equals(\"c\")&&input.length>0";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "!<m:equals>||input.length<=0"
                            : "!(instance type String).equals(\"c\")||input.length<=0";
                    assertEquals(expected, d.state().toString());
                }
            }
        };
        testClass("ConstantRange_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("s.equals(\"c\")", d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if(d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("<return value>", d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0.0.0.0.0".equals(d.statementId())) {
                    assertEquals("s.equals(\"c\")", d.condition().toString());
                    assertEquals("false", d.absoluteState().toString());
                }
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("s.equals(\"a\")||s.equals(\"b\")", d.condition().toString());
                }
            }
        };
        testClass("ConstantRange_1", 2, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
