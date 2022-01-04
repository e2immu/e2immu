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
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_01_Loops_2 extends CommonTestRunner {

    public Test_01_Loops_2() {
        super(true);
    }

    @Test
    public void test_2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("{\"a\",\"b\",\"c\"}", d.evaluationResult().value().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, d.evaluationResult().value()
                            .getProperty(d.evaluationResult().evaluationContext(), NOT_NULL_EXPRESSION, true));
                }
                if ("1.0.0".equals(d.statementId()) && d.iteration() > 0) {
                    assertEquals("s$1", d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("s".equals(d.variableName())) {
                    assertEquals("java.lang.String", d.variableInfo().variable()
                            .parameterizedType().typeInfo.fullyQualifiedName);
                    if ("1".equals(d.statementId())) {
                        String expectValue = "nullable instance type String";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));

                        assertEquals("s:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<v:s>" : "nullable instance type String";
                        assertEquals(expectValue, d.currentValue().toString());

                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));

                        String expectLv = d.iteration() == 0 ? "res:0,s:0" : "res:0,s$1:1,s:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("s$1".equals(d.variableName())) {
                    assertTrue(d.iteration() > 0);
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type String", d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(NOT_NULL_EXPRESSION));
                    }
                }
                if ("res$1".equals(d.variableName())) {
                    assertTrue(d.iteration() > 0);
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, d.getProperty(IMMUTABLE));
                    }
                }
                if ("res".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<v:s>" : "s$1";
                        assertEquals(expectValue, d.currentValue().toString());
                        String expectLv = d.iteration() == 0 ? "res:0,s:0" : "res:0,s$1:1,s:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<merge:String>" : "instance type String";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<merge:String>" : "instance type String";
                        assertEquals(expectValue, d.currentValue().toString());
                        String expectLv = "res:0,return method:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<merge:String>" : "res"; // indirection
                        assertEquals(expect, d.currentValue().toString());

                        String expectLv = "res:0,return method:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                }
            }
        };

        // overwrite assignment, because loop is guaranteed to be executed, and assignment is guaranteed to be
        // executed inside the block
        testClass("Loops_2", 1, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}