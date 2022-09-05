
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

package org.e2immu.analyser.parser.basics;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_00_Basics_23 extends CommonTestRunner {

    public Test_00_Basics_23() {
        super(false);
    }

    @Test
    public void test() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<f:a.i>==<f:c.i>" : "true";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
            if ("method0".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<f:(k<3?a:b).i>" : "(k<=2?new A(1):new A(2)).i";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("3.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "1==<f:(k<3?a:b).i>" : "1==(k<=2?new A(1):new A(2)).i"; // TODO should be "true"
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
            if ("method1".equals(d.methodInfo().name)) {
                if ("3.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<f:b.i>==<f:c.i>" : "true";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<f:b.i>==<f:c.i>" : "true";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method0".equals(d.methodInfo().name)) {
                if (d.variableInfoContainer().variableNature() instanceof VariableNature.ScopeVariable) {
                    assertTrue(d.variableName().startsWith("scope-"));
                    String expected = d.iteration() == 0 ? "k<=2?<new:A>:<new:A>" : "k<=2?new A(1):new A(2)";
                    assertEquals(expected, d.currentValue().toString());
                }
                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    if (fr.scopeVariable instanceof LocalVariableReference lvr) {
                        String expected = d.iteration() == 0 ? "<f:(k<3?a:b).i>" : "instance type int";
                        if ("a".equals(lvr.simpleName())) {
                            assertEquals(expected, d.currentValue().toString());
                        } else if ("b".equals(lvr.simpleName())) {
                            assertEquals(expected, d.currentValue().toString());
                        } else if (lvr.simpleName().startsWith("scope-")) {
                            assertEquals(expected, d.currentValue().toString());
                        } else fail();
                    } else fail();
                }
                if ("j".equals(d.variableName())) {
                    String expected = d.iteration() == 0 ? "<f:(k<3?a:b).i>" : "(k<=2?new A(1):new A(2)).i";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
            if ("method1".equals(d.methodInfo().name)) {
                if ("a".equals(d.variableName())) {
                    String expected = d.iteration() == 0 ? "<new:A>" : "new A(1)";
                    assertEquals(expected, d.currentValue().toString());
                }
                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<f:i>" : "instance type int";
                        assertEquals(expected, d.currentValue().toString());
                        assertEquals("b", fr.scope.toString());
                        assertNotNull(fr.scopeVariable);
                        assertEquals("b", fr.scopeVariable.toString());
                    }
                }
                if ("b".equals(d.variableName())) {
                    if ("3.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "k<=2?<new:A>:<new:A>" : "k<=2?new A(1):new A(2)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if ("b".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        // direct assignment variables (see Assignment, Expression.directAssignmentVariables)
                        assertEquals("a:0,c:0", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.TRUE_DV, Property.CNN_TRAVELS_TO_PRECONDITION);
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<v:b>" : "k<=2?a:c";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    if ("1.0.0".equals(d.statementId())) {
                        assertNotNull(fr.scopeVariable);
                        if ("c".equals(fr.scopeVariable.simpleName())) {
                            String expected = d.iteration() == 0 ? "<f:c.i>" : "instance type int";
                            assertEquals(expected, d.currentValue().toString());
                        }
                    }
                }
            }
        };
        // 2x 2 warnings, assert always true
        // TODO 7 would be better, we'd need another round of evaluation
        testClass("Basics_23", 0, 5, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
