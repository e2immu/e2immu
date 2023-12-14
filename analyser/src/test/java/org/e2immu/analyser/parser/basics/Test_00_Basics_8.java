
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

import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.BreakDelayVisitor;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.CONTEXT_NOT_NULL;
import static org.junit.jupiter.api.Assertions.*;

public class Test_00_Basics_8 extends CommonTestRunner {
    public Test_00_Basics_8() {
        super(true);
    }

    // assignment ids for local variables
    @Test
    public void test8() throws IOException {
        final String TYPE = "org.e2immu.analyser.parser.basics.testexample.Basics_8";
        final String I = TYPE + ".i";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test1".equals(d.methodInfo().name)) {
                if ("v".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfo().isAssigned());
                        assertEquals("l", d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId()) || "4".equals(d.statementId()) || "5".equals(d.statementId())) {
                        assertEquals("3" + Stage.EVALUATION,
                                d.variableInfo().getAssignmentIds().toString());
                        assertEquals("1+l", d.currentValue().toString());
                    }
                    if ("6".equals(d.statementId())) {
                        assertEquals("6" + Stage.EVALUATION,
                                d.variableInfo().getAssignmentIds().toString());
                        assertEquals("2+l", d.currentValue().toString());
                    }
                }
                if ("w".equals(d.variableName()) && "1".equals(d.statementId())) {
                    assertEquals("1+l", d.currentValue().toString());
                }
                if ("u".equals(d.variableName()) && ("4".equals(d.statementId()) || "5".equals(d.statementId()))) {
                    assertEquals("3+l", d.currentValue().toString());
                }
                if (d.variable() instanceof ParameterInfo p && "l".equals(p.name)) {
                    assertEquals("instance type int/*@Identity*/", d.currentValue().toString());
                }
            }

            if ("test3".equals(d.methodInfo().name) && d.iteration() > 0) {
                if ("j".equals(d.variableName()) && "0".equals(d.statementId())) {
                    assertEquals("i", d.currentValue().toString());
                }
                if (I.equals(d.variableName()) && "1".equals(d.statementId())) {
                    assertEquals("i+q", d.currentValue().toString());
                }
                if ("k".equals(d.variableName()) && "2".equals(d.statementId())) {
                    assertEquals("i+q", d.currentValue().toString());
                }
            }
            if ("test4".equals(d.methodInfo().name)) {
                String linkedVariables = d.variableInfo().getLinkedVariables().toString();
                if ("j".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "q:-1,this.i:0,this:-1" : "this.i:0,this:2";
                        assertEquals(linked, linkedVariables, d.statementId());
                        String expectValue = d.iteration() == 0 ? "<f:i>" : "i$1";
                        assertEquals(expectValue, d.currentValue().toString());
                        if (d.iteration() == 0) {
                            if (d.currentValue() instanceof DelayedVariableExpression dve) {
                                assertEquals(1, dve.statementTime);
                            } else fail();
                        }
                    }
                    if ("3".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:i>" : "i$1";
                        assertEquals(expectValue, d.currentValue().toString());
                        String linked = d.iteration() == 0 ? "k:0,q:-1,this.i:0,this:-1" : "k:0,this.i:0,this:2";
                        assertEquals(linked, linkedVariables, d.statementId());
                    }
                    if ("4.0.0.0.0".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "System.out:-1,j0:0,k:0,q:-1,this.i:0,this:-1"
                                : "j0:0,k:0,this.i:0,this:2";
                        assertEquals(linked, linkedVariables, d.statementId());
                    }
                    if ("4.0.0.0.1".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "System.out:-1,j0:0,k:0,q:-1,this.i:-1,this:-1"
                                : "j0:0,k:0,this:2";
                        assertEquals(linked, linkedVariables);
                    }
                    if ("4.0.0.0.2".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "System.out:-1,j0:0,k0:-1,k:0,q:-1,this.i:-1,this:-1"
                                : "j0:0,k:0,this:2";
                        assertEquals(linked, linkedVariables);
                    }
                    if ("4.0.0".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "System.out:-1,k:0,q:-1,this.i:-1,this:-1" : "k:0,this:2";
                        assertEquals(linked, linkedVariables);
                    }
                    if ("4".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "System.out:-1,k:0,q:-1,this.i:0,this:-1"
                                : "k:0,this.i:0,this:2";
                        assertEquals(linked, linkedVariables, d.statementId());
                    }
                }
                if ("k".equals(d.variableName())) {
                    if ("3".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:i>" : "i$2";
                        assertEquals(expectValue, d.currentValue().toString());
                        if (d.iteration() == 0) {
                            if (d.currentValue() instanceof DelayedVariableExpression dve) {
                                assertEquals(2, dve.statementTime);
                            } else fail();
                        }
                    }
                }
                if (d.iteration() > 0) {
                    if ("j0".equals(d.variableName()) && "4.0.0.0.0".equals(d.statementId())) {
                        assertEquals("i", d.currentValue().toString());
                    }
                    if ("k0".equals(d.variableName()) && "4.0.0.0.2".equals(d.statementId())) {
                        assertEquals("i+q", d.currentValue().toString());
                    }
                }
                if ("java.lang.System.out".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:out>" : "instance type PrintStream/*@IgnoreMods*/";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("4".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:out>" : "instance type PrintStream/*@IgnoreMods*/";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                }
                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo().name)) {
                    if ("4.0.0.0.1".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "System.out:-1,j0:-1,j:-1,k:-1,q:-1,this:-1" : "this:2";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("4.0.0".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "System.out:-1,j:-1,k:-1,q:-1,this:-1" : "this:2";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("4".equals(d.statementId())) {
                        // the j:0, k:0 come from the fact that the if-block is only executed conditionally, and so,
                        // in case of j!=k, they both still have been assigned.
                        String linked = d.iteration() == 0 ? "System.out:-1,j:0,k:0,q:-1,this:-1" : "j:0,k:0,this:2";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test4".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<m:println>" : "<no return value>";
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                }
                if ("4".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<f:i>==<f:i>" : "i$1==i$2";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("4.0.0.0.3".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<simplification>" : "true";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {

            if ("test4".equals(d.methodInfo().name)) {
                int timeI = d.statementAnalysis().statementTime(Stage.INITIAL);
                int timeE = d.statementAnalysis().statementTime(Stage.EVALUATION);
                int timeM = d.statementAnalysis().statementTime(Stage.MERGE);

                if ("0".equals(d.statementId())) {
                    assertEquals(0, timeI);
                    assertEquals(1, timeE);
                    assertEquals(1, timeM);
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(1, timeI);
                    assertEquals(1, timeE);
                    assertEquals(1, timeM);
                }
                if ("2".equals(d.statementId())) {
                    assertEquals(1, timeI);
                    assertEquals(2, timeE);
                    assertEquals(2, timeM);
                }
                if ("4".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                }
                if ("4.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<f:i>==<f:i>" : "i$1==i$2";
                    assertEquals(expected, d.condition().toString());
                }
                if ("4.0.0.0.3".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<simplification>&&<f:i>==<f:i>" : "i$1==i$2";
                    assertEquals(expected, d.absoluteState().toString());
                }
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----", d.delaySequence());

        testClass("Basics_8", 0, 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }
}
