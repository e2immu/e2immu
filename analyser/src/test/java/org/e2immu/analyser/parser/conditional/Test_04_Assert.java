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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_04_Assert extends CommonTestRunner {
    public Test_04_Assert() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("combine".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = "other.isDelayed()";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1 -> "<m:isDelayed>";
                        case 2 -> "!<m:isEmpty>";
                        default -> "!causes.isEmpty()";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("combine".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    // not delayed, even in iteration 0: call to interface method
                    String expected = "Precondition[expression=other.isDelayed(), causes=[escape]]";
                    assertEquals(expected, d.statementAnalysis().stateData().getPrecondition().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1 -> "Precondition[expression=<m:isDelayed>, causes=[escape]]";
                        case 2 -> "Precondition[expression=!<m:isEmpty>, causes=[escape]]";
                        default -> "Precondition[expression=!causes.isEmpty(), causes=[escape]]";
                    };
                    assertEquals(expected, d.statementAnalysis().stateData().getPrecondition().toString());
                }
            }
        };
        testClass("Assert_0", 0, 4, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setForceAlphabeticAnalysisInPrimaryType(true).build());
    }


    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "strings".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        // no Annotated APIs...
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("containsA".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "set".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            String pc = d.statementAnalysis().stateData().getPrecondition().toString();
            if ("test".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "Precondition[expression=<precondition>, causes=[methodCall:containsA]]"
                            : "Precondition[expression=!strings.contains(\"a\"), causes=[methodCall:containsA]]";
                    assertEquals(expected, pc);
                }
            }
            if ("containsA".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("Precondition[expression=!set.contains(\"a\"), causes=[escape]]", pc);
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("Precondition[expression=true, causes=[]]", pc);
                }
            }
        };
        // ignoring result of method call -> 1 warning
        testClass("Assert_1", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }


    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "strings".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        // no Annotated APIs...
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("containsA".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "set".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            String pc = d.statementAnalysis().stateData().getPrecondition().toString();
            if ("test".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "Precondition[expression=<precondition>, causes=[escape, methodCall:containsA]]"
                            : "Precondition[expression=!strings.contains(\"a\"), causes=[escape, methodCall:containsA]]";
                    assertEquals(expected, pc);
                }
            }
            if ("containsA".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("Precondition[expression=!set.contains(\"a\"), causes=[escape]]", pc);
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("Precondition[expression=true, causes=[]]", pc);
                }
            }
        };
        // ignoring result of method call -> 1 warning
        testClass("Assert_2", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }
}
