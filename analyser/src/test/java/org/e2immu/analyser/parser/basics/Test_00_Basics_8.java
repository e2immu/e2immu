
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
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.CONTEXT_MODIFIED;
import static org.e2immu.analyser.analyser.Property.CONTEXT_NOT_NULL;
import static org.junit.jupiter.api.Assertions.*;

public class Test_00_Basics_8 extends CommonTestRunner {
    public Test_00_Basics_8() {
        super(true);
    }

    // assignment ids for local variables
    @Test
    public void test8() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Basics_8";
        final String I = TYPE + ".i";
        final String I0 = "i$0";
        final String I1 = "i$1";
        final String I2 = "i$2";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test1".equals(d.methodInfo().name)) {
                if ("v".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfo().isAssigned());
                        assertEquals("l", d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId()) || "4".equals(d.statementId()) || "5".equals(d.statementId())) {
                        assertEquals("3" + VariableInfoContainer.Level.EVALUATION,
                                d.variableInfo().getAssignmentIds().toString());
                        assertEquals("1+l", d.currentValue().toString());
                    }
                    if ("6".equals(d.statementId())) {
                        assertEquals("6" + VariableInfoContainer.Level.EVALUATION,
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
                if(d.variable() instanceof ParameterInfo p && "l".equals(p.name)) {
                    assertEquals("instance type int/*@Identity*/", d.currentValue().toString());
                }
            }

            if ("test3".equals(d.methodInfo().name) && d.iteration() > 0) {
                if ("j".equals(d.variableName()) && "0".equals(d.statementId())) {
                    assertEquals(I0, d.currentValue().toString());
                }
                if (I.equals(d.variableName()) && "1".equals(d.statementId())) {
                    assertEquals(I0 + "+q", d.currentValue().toString());
                }
                if ("k".equals(d.variableName()) && "2".equals(d.statementId())) {
                    assertEquals(I0 + "+q", d.currentValue().toString());
                }
            }
            if ("test4".equals(d.methodInfo().name)) {
                String linkedVariables = d.variableInfo().getLinkedVariables().toString();
                if ("j".equals(d.variableName())) {
                    if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        assertEquals("j:0,this.i:0", linkedVariables, d.statementId());
                    }
                    if ("3".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:i>" : I1;
                        assertEquals(expectValue, d.currentValue().toString());
                        assertEquals("j:0,k:0,this.i:0", linkedVariables, d.statementId());
                    }
                    if ("4.0.0.0.0".equals(d.statementId())) {
                        assertEquals("j0:0,j:0,k:0,this.i:0", linkedVariables, d.statementId());
                    }
                    if ("4.0.0.0.1".equals(d.statementId()) || "4.0.0.0.2".equals(d.statementId()) ||
                            "4.0.0.0.3".equals(d.statementId()) || "4.0.0.0.4".equals(d.statementId())) {
                        assertEquals("j0:0,j:0,k:0", linkedVariables, "At " + d.statementId());
                    }
                    if ("4.0.0".equals(d.statementId())) {
                        assertEquals("j:0,k:0", linkedVariables, "At " + d.statementId());
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals("j:0,k:0,this.i:0", linkedVariables, d.statementId());
                    }
                }
                if ("k".equals(d.variableName())) {
                    if ("3".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:i>" : I2;
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
                if (d.iteration() > 0) {
                    if ("j0".equals(d.variableName()) && "4.0.0.0.0".equals(d.statementId())) {
                        assertEquals(I2, d.currentValue().toString());
                    }
                    if (I.equals(d.variableName()) && "4.0.0.0.1".equals(d.statementId())) {
                        assertEquals(I2 + "+q", d.currentValue().toString());
                    }
                    if (I1.equals(d.variable().simpleName())) {
                        if ("3".equals(d.statementId())) {
                            assertEquals("i$1:0,i$2:1,j:1,k:1,this.i:1", d.variableInfo().getLinkedVariables().toString());
                            assertFalse(d.variableInfo().getLinkedVariables().isDelayed());
                            assertEquals(DV.FALSE_DV, d.getProperty(CONTEXT_MODIFIED));
                        }
                        if ("4.0.0.0.0".equals(d.statementId())) {
                            assertEquals("i$1:0,i$2:1,j0:1,j:1,k:1,this.i:1", d.variableInfo().getLinkedVariables().toString());
                            assertFalse(d.variableInfo().getLinkedVariables().isDelayed());
                            assertEquals(DV.FALSE_DV, d.getProperty(CONTEXT_MODIFIED));

                            assertEquals("instance type int", d.currentValue().toString());
                        }
                        if ("4.0.0.0.2".equals(d.statementId())) {
                            assertEquals("i$1:0,i$2:1,j0:1,j:1,k:1", linkedVariables);
                            assertFalse(d.variableInfo().getLinkedVariables().isDelayed());
                            assertEquals(DV.FALSE_DV, d.getProperty(CONTEXT_MODIFIED));

                            assertEquals("instance type int", d.currentValue().toString());
                            assertDv(d, 0, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                        }
                        if ("4.0.0.0.3".equals(d.statementId())) {
                            assertEquals("i$1:0,i$2:1,j0:1,j:1,k:1", linkedVariables);
                            assertFalse(d.variableInfo().getLinkedVariables().isDelayed());
                            assertEquals(DV.FALSE_DV, d.getProperty(CONTEXT_MODIFIED));

                            assertEquals("instance type int", d.currentValue().toString());
                            assertDv(d, 0, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                        }
                        if ("4".equals(d.statementId())) {
                            assertEquals("i$1:0,i$2:1,j:1,k:1,this.i:1", linkedVariables);
                            assertFalse(d.variableInfo().getLinkedVariables().isDelayed());
                            assertEquals(DV.FALSE_DV, d.getProperty(CONTEXT_MODIFIED));
                        }
                    }
                    if ("k0".equals(d.variableName()) && "4.0.0.0.2".equals(d.statementId())) {
                        assertEquals(I2 + "+q", d.currentValue().toString());
                    }
                }
                if ("java.lang.System.out".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:out>" : "instance type PrintStream";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("4".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:out>" : "instance type PrintStream";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test4".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<m:println>" : "<no return value>";
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
            if ("test4".equals(d.methodInfo().name) && d.iteration() > 0 && "4.0.0.0.3".equals(d.statementId())) {
                assertEquals("true", d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {

            if ("test4".equals(d.methodInfo().name)) {
                int timeI = d.statementAnalysis().statementTime(VariableInfoContainer.Level.INITIAL);
                int timeE = d.statementAnalysis().statementTime(VariableInfoContainer.Level.EVALUATION);
                int timeM = d.statementAnalysis().statementTime(VariableInfoContainer.Level.MERGE);

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
            }
            if ("4.0.0.0.0".equals(d.statementId()) && d.iteration() > 0) {
                assertEquals(I1 + "==" + I2, d.absoluteState().toString());
            }
        };

        testClass("Basics_8", 0, 3, new DebugConfiguration.Builder()
             //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
             //   .addStatementAnalyserVisitor(statementAnalyserVisitor)
             //   .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }
}
