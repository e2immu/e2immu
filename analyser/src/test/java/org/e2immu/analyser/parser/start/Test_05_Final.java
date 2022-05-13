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

package org.e2immu.analyser.parser.start;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.MultiValue;
import org.e2immu.analyser.model.expression.StringConcat;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analysis.FlowData.ALWAYS;
import static org.e2immu.analyser.analysis.FlowData.NEVER;
import static org.junit.jupiter.api.Assertions.*;

/*
 Aggregate test
 */
public class Test_05_Final extends CommonTestRunner {
    public Test_05_Final() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        final String FINAL_CHECKS = "Final_0";
        final String TYPE = "org.e2immu.analyser.parser.start.testexample." + FINAL_CHECKS;
        // there are 2 constructors, with different parameter lists
        final String FINAL_CHECKS_FQN = TYPE + "." + FINAL_CHECKS + "(java.lang.String,java.lang.String)";

        final String S1 = TYPE + ".s1";
        final String S4 = TYPE + ".s4";
        final String S4_0 = "s4$0";
        final String S5 = TYPE + ".s5";
        final String THIS = TYPE + ".this";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setS4".equals(d.methodInfo().name)) {
                if (S4.equals(d.variableName())) {
                    assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(NOT_NULL_EXPRESSION));
                    assertEquals("s4", d.currentValue().toString());
                    assertEquals("s4:0", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ParameterInfo pi && "s4".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("this.s4:0", d.variableInfo().getLinkedVariables().toString());

                        // p4 never came in a not-null context
                        assertTrue(d.variableInfo().isRead());
                    } else fail();
                }
            }
            if ("toString".equals(d.methodInfo().name) && FINAL_CHECKS.equals(d.methodInfo().typeInfo.simpleName)) {
                if (S4_0.equals(d.variableName())) {
                    assertTrue(d.iteration() > 0);
                    String expectValue = d.iteration() == 1 ? "<v:" + S4_0 + ">" : "nullable instance type String";
                    assertEquals(expectValue, d.currentValue().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue;
                    if (d.iteration() == 0) {
                        expectValue = "<f:s1>+\" \"+<f:s2>+\" \"+<f:s3>+\" \"+<f:s4>";
                    } else if (d.iteration() == 1) {
                        expectValue = "<f:s1>+\" \"+<f:s2>+\" \"+\"abc\"+\" \"+" + S4_0;
                    } else {
                        expectValue = "s1+\" \"+s2+\" \"+\"abc\"+\" \"+" + S4_0;
                    }
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(d.iteration() > 1, d.variableInfo().valueIsSet());
                }
            }

            if (FINAL_CHECKS.equals(d.methodInfo().name) && d.methodInfo().methodInspection.get().getParameters().size() == 2) {
                assertEquals(FINAL_CHECKS_FQN, d.methodInfo().fullyQualifiedName);
                if (THIS.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("0:M", d.variableInfo().getReadId());
                        assertEquals("instance type " + FINAL_CHECKS, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("1-E", d.variableInfo().getReadId());
                        assertEquals("instance type " + FINAL_CHECKS, d.currentValue().toString());
                    }
                }
                if (S1.equals(d.variableName())) {
                    String expectValue = d.iteration() == 0 ? "<s:String>" : "s1+\"abc\"";
                    assertEquals(expectValue, d.currentValue().toString());

                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);

                    if (d.iteration() > 0) {
                        assertTrue(d.currentValue().isInstanceOf(StringConcat.class));
                    }
                    assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                }
                if (S5.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<null-check>?\"abc\":null" : "\"abc\"";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("\"abc\"", d.currentValue().toString());
                        VariableInfo viE = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, viE.getProperty(IMMUTABLE));
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                    }
                    if ("0.1.0".equals(d.statementId())) {
                        assertEquals("null", d.currentValue().toString());
                        VariableInfo viE = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, viE.getProperty(IMMUTABLE));
                        assertDv(d, MultiLevel.NULLABLE_DV, NOT_NULL_EXPRESSION);
                    }
                }
            }

        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if (FINAL_CHECKS_FQN.equals(d.methodInfo().fullyQualifiedName())) {
                assertTrue(d.statementAnalysis().inSyncBlock());
                if ("0.0.1".equals(d.statementId())) {
                    fail(); // unreachable
                }
                if ("1.0.1".equals(d.statementId())) {
                    fail(); // statement unreachable
                }
                if ("0".equals(d.statementId())) {
                    FlowData fd0 = d.statementAnalysis().navigationData().blocks.get().get(0).orElseThrow().flowData();
                    FlowData fd1 = d.statementAnalysis().navigationData().blocks.get().get(1).orElseThrow().flowData();
                    if (d.iteration() == 0) {
                        assertTrue(fd0.getGuaranteedToBeReachedInMethod().isDelayed());
                        assertTrue(fd1.getGuaranteedToBeReachedInMethod().isDelayed());
                        assertEquals(ALWAYS, fd0.getGuaranteedToBeReachedInCurrentBlock());
                        assertEquals(ALWAYS, fd1.getGuaranteedToBeReachedInCurrentBlock());
                    } else {
                        assertEquals(ALWAYS, fd0.getGuaranteedToBeReachedInMethod());
                        assertEquals(NEVER, fd1.getGuaranteedToBeReachedInMethod());
                        assertTrue(fd1.isUnreachable());
                    }
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(d.iteration() > 0,
                            d.statementAnalysis().navigationData().blocks.get().get(0).orElseThrow().flowData().isUnreachable());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            TypeInfo stringType = d.evaluationContext().getPrimitives().stringTypeInfo();
            assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV,
                    stringType.typeAnalysis.get().getProperty(IMMUTABLE));
            MethodInfo methodInfo = d.methodInfo();

            if ("setS4".equals(methodInfo.name)) {
                assertDv(d.p(0), 1, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s1".equals(d.fieldInfo().name)) {
                String expect = d.iteration() == 0 ? "<f:s1>" : "[s1+\"abc\",s1]";
                assertEquals(expect, d.fieldAnalysis().getValue().toString());
                assertEquals("s1:0", d.fieldAnalysis().getLinkedVariables().toString());
            }
            if ("s2".equals(d.fieldInfo().name)) {
                String expectValue = d.iteration() == 0 ? "<f:s2>" : "[null,s2]";
                if (d.iteration() > 0) {
                    assertTrue(d.fieldAnalysis().getValue() instanceof MultiValue);
                }
                assertEquals(expectValue, d.fieldAnalysis().getValue().toString());
                assertEquals("s2:0", d.fieldAnalysis().getLinkedVariables().toString());
            }
            if ("s4".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.NULLABLE_DV, d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));
            }
            if ("s5".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(FINAL));
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if (FINAL_CHECKS_FQN.equals(d.methodInfo().fullyQualifiedName())) {
                if ("0".equals(d.statementId())) {
                    // this.s5 == null

                    // first, show that THIS is read
                    EvaluationResult.ChangeData changeData = d.findValueChange(THIS);
                    assertEquals("[0]", changeData.readAtStatementTime().toString());

                    // null==s5 should become true because initially, s5 in the constructor IS null
                    String expect = d.iteration() == 0 ? "<null-check>" : "true";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("3".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "s1+<f:s3>" : "s1+\"abc\"";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
            }
        };

        testClass(FINAL_CHECKS, 5, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("Final_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
