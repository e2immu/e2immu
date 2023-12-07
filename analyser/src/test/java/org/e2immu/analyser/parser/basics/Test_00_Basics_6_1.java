
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
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.expression.UnknownExpression;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintStream;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_00_Basics_6_1 extends CommonTestRunner {
    public Test_00_Basics_6_1() {
        super(true);
    }

    // basic statement timing, this time inside an expression rather than across statements as in Basics_6
    @Test
    public void test6() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test1".equals(d.methodInfo().name)) {
                if ("f1".equals(d.variableName()) && "1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<f:field>" : "field$0";
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("f2".equals(d.variableName()) && "1".equals(d.statementId())) {
                    // IMPORTANT: we expect field$1 here, not field$0: interrupting has caused time to increment
                    String expect = d.iteration() == 0 ? "<f:field>" : "field$1";
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("r".equals(d.variableName()) && "1".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "4+<f:field>+<f:field>" : "4+field$0+field$1";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
            if ("test2".equals(d.methodInfo().name)) {
                if ("f1".equals(d.variableName()) && "1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<f:field>" : "field$0";
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("f2".equals(d.variableName()) && "1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<f:field>" : "field$0";
                    assertEquals(expect, d.currentValue().toString());
                }
                if ("n".equals(d.variableName()) && "1".equals(d.statementId())) {
                    assertEquals("/*inline someMinorMethod*/Math.pow(i,3)/*(int)*/", d.currentValue().toString());
                }
                if ("r".equals(d.variableName()) && "1".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "2*<f:field>+(/*inline someMinorMethod*/Math.pow(i,3)/*(int)*/)"
                            : "2*field$0+(/*inline someMinorMethod*/Math.pow(i,3)/*(int)*/)";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int timeI = d.statementAnalysis().statementTime(Stage.INITIAL);
            int timeE = d.statementAnalysis().statementTime(Stage.EVALUATION);
            int timeM = d.statementAnalysis().statementTime(Stage.MERGE);
            int numVariables = d.statementAnalysis().numberOfVariables();

            if ("test1".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(4, numVariables);
                        assertEquals(0, timeI);
                        assertEquals(0, timeE);
                        assertEquals(0, timeM);
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals(0, timeI);
                        assertEquals(1, timeE);
                        assertEquals(1, timeM);
                        // vars: field, this, f1, f2, n, r
                        assertEquals(6, numVariables);
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals(1, timeI);
                        assertEquals(1, timeE);
                        assertEquals(1, timeM);
                        // vars: field, this, f1, f2, n, r
                        assertEquals(6, numVariables);
                    }
                } else if (d.iteration() == 1) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(4, numVariables);
                    }
                }
                if ("2".equals(d.statementId())) {
                    assertNull(d.haveError(Message.Label.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                }
            }
            if ("test2".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(4, numVariables);
                        assertEquals(0, timeI);
                        assertEquals(0, timeE);
                        assertEquals(0, timeM);
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals(0, timeI);
                        assertEquals(0, timeE);
                        assertEquals(0, timeM);
                        // vars: field, this, f1, f2, n, r
                        assertEquals(6, numVariables);
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals(0, timeI);
                        assertEquals(0, timeE);
                        assertEquals(0, timeM);
                        // vars: field, this, f1, f2, n, r
                        assertEquals(6, numVariables);
                    }
                } else if (d.iteration() == 1) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(4, numVariables);
                    }
                }
                if ("2".equals(d.statementId())) {
                    assertEquals(d.iteration() > 0,
                            null != d.haveError(Message.Label.ASSERT_EVALUATES_TO_CONSTANT_TRUE));
                }
            }
        };

        TypeMapVisitor typeMapVisitor = d -> {
            TypeMap typeMap = d.typeMap();
            TypeInfo system = typeMap.get(System.class);
            FieldInfo out = system.getFieldByName("out", true);
            assertEquals(DV.TRUE_DV, out.fieldAnalysis.get().getProperty(FINAL));
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                    d.getFieldAnalysis(out).getProperty(EXTERNAL_NOT_NULL));

            TypeInfo string = typeMap.get(String.class);
            MethodInfo equals = string.findUniqueMethod("equals", 1);
            assertEquals(DV.FALSE_DV, d.getMethodAnalysis(equals).getProperty(MODIFIED_METHOD));

            MethodInfo toLowerCase = string.findUniqueMethod("toLowerCase", 0);
            assertFalse(toLowerCase.methodResolution.get().allowsInterrupts());

            MethodInfo toUpperCase = string.findUniqueMethod("toUpperCase", 0);
            assertFalse(toLowerCase.methodResolution.get().allowsInterrupts());
            assertEquals(DV.FALSE_DV, d.getMethodAnalysis(toUpperCase).getProperty(MODIFIED_METHOD));

            TypeInfo printStream = typeMap.get(PrintStream.class);
            MethodInfo println = printStream.findUniqueMethod("println", 0);
            assertTrue(println.methodResolution.get().allowsInterrupts());
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("someMinorMethod".equals(d.methodInfo().name)) {
                String expected = "/*inline someMinorMethod*/Math.pow(i,3)/*(int)*/";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());

                assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
                assertEquals("", d.methodInfo().methodResolution.get().methodsOfOwnClassReachedSorted());
                assertFalse(d.methodInfo().methodResolution.get().allowsInterrupts());

                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(MODIFIED_METHOD));
            }
            if ("interrupting".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().methodResolution.get().allowsInterrupts());
                assertTrue(d.methodAnalysis().getPrecondition().isEmpty());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("field".equals(d.fieldInfo().name)) {
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(FINAL));
                assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
                assertTrue(d.fieldAnalysis().getValue() instanceof UnknownExpression);
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test2".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                assertTrue(d.iteration() == 0 || d.evaluationResult().causesOfDelay().isDone(),
                        "Got " + d.evaluationResult().causesOfDelay());

                String expectValue = d.iteration() == 0 ? "<simplification>" : "true";
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Basics_6".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.MUTABLE_DV, IMMUTABLE);
                mustSeeIteration(d, 1);
            }
        };

        testClass("Basics_6_1", 2, 1, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }
}
