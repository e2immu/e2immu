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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MultiValue;
import org.e2immu.analyser.model.expression.StringConcat;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.FlowData.Execution.*;
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
        final String TYPE = "org.e2immu.analyser.testexample." + FINAL_CHECKS;
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
                    assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    assertEquals("s4", d.currentValue().debugOutput());
                    assertEquals("s4:0,this.s4:0", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ParameterInfo pi && "s4".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertFalse(d.hasProperty(VariableProperty.MODIFIED_VARIABLE)); // no method was called on parameter s4
                        assertEquals("s4:0,this.s4:0", d.variableInfo().getLinkedVariables().toString());

                        // p4 never came in a not-null context
                        assertTrue(d.variableInfo().isRead());
                        int expectNotNull = d.iteration() <= 2 ? Level.DELAY : MultiLevel.NULLABLE;
                        assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL_PARAMETER)); // nothing that points to not null
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
                    String expectValue = d.iteration() == 0 ? "s1+<f:s3>" : "s1+\"abc\"";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION));
                    int expected = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expected, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION)); // nothing that points to not null

                    if (d.iteration() > 0) {
                        assertTrue(d.currentValue().isInstanceOf(StringConcat.class));
                    }
                    assertEquals("this.s1:0", d.variableInfo().getLinkedVariables().toString());
                }
                if (S5.equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals(Level.DELAY, d.getProperty(VariableProperty.CONTEXT_NOT_NULL_FOR_PARENT_DELAY));
                    }

                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "null==<f:s5>?\"abc\":null" : "\"abc\"";
                        assertEquals(expectValue, d.currentValue().toString());
                        VariableInfo viC = d.variableInfoContainer().getPreviousOrInitial();
                        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE, viC.getProperty(VariableProperty.IMMUTABLE));
                        VariableInfo viM = d.variableInfoContainer().current();
                        int expectM = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE;
                        assertEquals(expectM, viM.getProperty(VariableProperty.IMMUTABLE));
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("\"abc\"", d.currentValue().toString());
                        VariableInfo viC = d.variableInfoContainer().getPreviousOrInitial();
                        int expectImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE;
                        assertEquals(expectImm, viC.getProperty(VariableProperty.IMMUTABLE));
                        VariableInfo viE = d.variableInfoContainer().best(VariableInfoContainer.Level.EVALUATION);
                        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE, viE.getProperty(VariableProperty.IMMUTABLE));
                    }
                }
            }

        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if (FINAL_CHECKS_FQN.equals(d.methodInfo().fullyQualifiedName())) {
                assertTrue(d.statementAnalysis().inSyncBlock);
                if ("0.0.1".equals(d.statementId())) {
                    fail(); // unreachable
                }
                if ("1.0.1".equals(d.statementId())) {
                    fail(); // statement unreachable
                }
                if ("0".equals(d.statementId())) {
                    FlowData fd0 = d.statementAnalysis().navigationData.blocks.get().get(0).orElseThrow().flowData;
                    FlowData fd1 = d.statementAnalysis().navigationData.blocks.get().get(1).orElseThrow().flowData;
                    if (d.iteration() == 0) {
                        assertEquals(DELAYED_EXECUTION, fd0.getGuaranteedToBeReachedInMethod());
                        assertEquals(DELAYED_EXECUTION, fd1.getGuaranteedToBeReachedInMethod());
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
                            d.statementAnalysis().navigationData.blocks.get().get(0).orElseThrow().flowData.isUnreachable());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            TypeInfo stringType = d.evaluationContext().getPrimitives().stringTypeInfo;
            assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE,
                    stringType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
            MethodInfo methodInfo = d.methodInfo();

            if ("setS4".equals(methodInfo.name)) {
                // @NotModified decided straight away, @Identity as well
                //     assertEquals(Level.FALSE, d.parameterAnalyses().get(0).getProperty(VariableProperty.MODIFIED_METHOD));
                ParameterAnalysis param = d.parameterAnalyses().get(0);
                int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                assertEquals(expectCnn, param.getProperty(VariableProperty.CONTEXT_NOT_NULL));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s1".equals(d.fieldInfo().name)) {
                if (d.iteration() > 0) {
                    // cannot properly be assigned/linked to one parameter
                    assertEquals("[s1+\"abc\",s1]", d.fieldAnalysis().getEffectivelyFinalValue().debugOutput());
                } else {
                    assertNull(d.fieldAnalysis().getEffectivelyFinalValue());
                }
                assertEquals("s1:0", d.fieldAnalysis().getLinkedVariables().toString());
            }
            if ("s2".equals(d.fieldInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.fieldAnalysis().getEffectivelyFinalValue());
                } else {
                    assertEquals("[null,s2]", d.fieldAnalysis().getEffectivelyFinalValue().debugOutput());
                    assertTrue(d.fieldAnalysis().getEffectivelyFinalValue() instanceof MultiValue);
                }
                assertEquals("s2:0", d.fieldAnalysis().getLinkedVariables().toString());
            }
            if ("s4".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
            if ("s5".equals(d.fieldInfo().name)) {
                assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
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
                    String expect = d.iteration() == 0 ? "null==<f:s5>" : "true";
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
