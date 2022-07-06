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
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.Or;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_07_DependentVariables extends CommonTestRunner {
    public Test_07_DependentVariables() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {

                String read = d.variableInfo().getReadId();
                String assigned = d.variableInfo().getAssignmentIds().getLatestAssignment();

                if ("array[0]".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("12", d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("1-E", assigned);
                        assertEquals("-", read);
                        assertEquals("12", d.variableInfo().getValue().toString());
                    }
                    if ("4".equals(d.statementId())) {
                        assertTrue(assigned.compareTo(read) < 0);
                        assertEquals("12", d.variableInfo().getValue().toString());
                    }
                } else if ("array[1]".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        assertEquals("2-E", assigned);
                        assertEquals("-", read);
                        assertEquals("13", d.currentValue().toString());
                    }
                    if ("4".equals(d.statementId())) {
                        assertTrue(assigned.compareTo(read) < 0);
                        assertEquals("13", d.variableInfo().getValue().toString());
                    }
                } else if ("array[2]".equals(d.variableName())) {
                    if ("4".equals(d.statementId())) {
                        assertEquals("3-E", assigned);
                        assertEquals("4-E", read);
                        assertEquals("31", d.variableInfo().getValue().toString());
                    }
                } else if ("array".equals(d.variableName())) {
                    if ("4".equals(d.statementId())) {
                        assertEquals("4" + Stage.EVALUATION, read);
                    }
                } else if (!(d.variable() instanceof This) && !(d.variable() instanceof ParameterInfo) && !(d.variable() instanceof ReturnVariable)) {
                    fail("have variable " + d.variableName());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name) && "4".equals(d.statementId())) {
                VariableInfo tv = d.getReturnAsVariable();
                assertEquals("56", tv.getValue().toString());
            }
        };

        // unused parameter in method1
        testClass("DependentVariables_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("getX".equals(d.methodInfo().name)) {
                assertEquals(5, d.evaluationResult().changeData().keySet().size());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("getI".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() == 0 ? "<f:i>" : "instance type int";
                    assertEquals(expectValue, d.currentValue().minimalOutput());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());

                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = d.iteration() == 0 ? "<f:i>" : "i$0";
                    assertEquals(expectValue, d.currentValue().minimalOutput());
                    assertEquals("this.i:0", d.variableInfo().getLinkedVariables().toString());

                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                }
            }
            if ("getX".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "xs".equals(fr.fieldInfo.name)) {
                    String expect = d.iteration() <= 1 ? "<f:xs>" : "instance type X[]";
                    assertEquals(expect, d.currentValue().toString());
                    assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    assertDv(d, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = switch (d.iteration()) {
                        case 0 -> "<dv:xs[index]>";
                        // from iteration 1, we know xs, and we know index; at iteration 1, we do not know the dependent variable
                        case 1 -> "<array-access:X>/*{L xs:independent1:3}*/";
                        default -> "xs[index]";
                    };
                    assertEquals(expectValue, d.currentValue().minimalOutput());

                    assertDv(d, 0, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                }
                if (d.variable() instanceof ParameterInfo) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
                if (d.variable() instanceof DependentVariable dv) {
                    assertEquals("xs[index]", dv.simpleName);
                    String expected = switch (d.iteration()) {
                        case 0, 1 -> "<array-access:X>/*{L xs:independent1:3}*/";
                        default -> "nullable instance type X/*{L xs:independent1:3}*/";
                    };
                    assertEquals(expected, d.currentValue().toString());
                    // DVE has no linking info (so this.xs:-1) goes out in iteration 0
                    String expectLv = switch (d.iteration()) {
                        case 0 -> "this.xs:-1";
                        default -> "this.xs:3";
                    };
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                    assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                }
            }
            if ("XS".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                if (d.variable() instanceof ParameterInfo p && "p".equals(p.name)) {
                    assertEquals("nullable instance type X[]/*@Identity*/", d.currentValue().toString());
                }
                if (d.variable() instanceof FieldReference fr && "xs".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("new X[](p.length)", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "p:-1" : "p:3";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getX".equals(d.methodInfo().name)) {
                assertDv(d, 2, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
            }
            if ("XS".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                assertDv(d.p(0), 2, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("XS".equals(d.typeInfo().simpleName)) {
                assertEquals("Type org.e2immu.analyser.parser.start.testexample.DependentVariables_1.X",
                        d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("xs".equals(d.fieldInfo().name)) {
                String expectLinked = d.iteration() == 0 ? "p:-1" : "p:3,xs[index]:3";
                assertEquals(expectLinked, d.fieldAnalysis().getLinkedVariables().toString());
                assertEquals(d.iteration() == 0, d.fieldAnalysis().getLinkedVariables().isDelayed());
                assertEquals("instance type X[]", d.fieldAnalysis().getValue().toString());
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
            }
            if ("i".equals(d.fieldInfo().name)) {
                assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.fieldAnalysis().getProperty(Property.EXTERNAL_NOT_NULL));
            }
        };

        testClass("DependentVariables_1", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }


    @Test
    public void test_2() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("XS".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                if (d.variable() instanceof FieldReference fr && "xs".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        String expectLinked = d.iteration() == 0 ? "xs:-1" : "xs:3";
                        assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("getX".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expectLinked = switch (d.iteration()) {
                        case 0, 1 -> "index:-1,this.xs:-1,xs[index]:-1";
                        default -> "this.xs:2,xs[index]:1";
                    };
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("xs".equals(d.fieldInfo().name)) {
                String linked = d.iteration() == 0 ? "xs:-1" : "xs:3";
                assertEquals(linked, d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("XS".equals(d.typeInfo().simpleName)) {
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getX".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 1 ? "<m:getX>" : "/*inline getX*/xs[index]";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                // has to be DEPENDENT, because X is not transparent
                assertDv(d, 2, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
            }
            if ("XS".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
            }
        };

        testClass("DependentVariables_2", 0, 1, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "<v:bs[0]>||<v:bs[1]>||<v:bs[2]>||<v:bs[3]>"
                            : "bs[0]||bs[1]||bs[2]||bs[3]";
                    assertEquals(expected, d.evaluationResult().value().minimalOutput());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("bs[0]".equals(d.variable().simpleName())) {
                    assertEquals("org.e2immu.analyser.parser.start.testexample.DependentVariables_3.method(boolean[]):0:bs[0]",
                            d.variable().fullyQualifiedName());
                    assertCurrentValue(d, 1, "instance type boolean");
                }
                if ("added".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("false", d.currentValue().toString());
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<dv:bs[0]>" : "bs[0]";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("0.0.2.0.2".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<v:added>" : "true";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("0.0.2".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<dv:bs[1]>?<v:added>:<dv:bs[0]>" : "bs[0]||bs[1]";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("0.0.3".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<dv:bs[1]>||<dv:bs[2]>?<v:added>:<dv:bs[0]>"
                                : "bs[0]||bs[1]||bs[2]";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("0.0.0".equals(d.statementId())) {
                if (d.condition() instanceof Or or) {
                    assertEquals(4, or.expressions().size());
                } else fail();
            }
            if ("0.0.1.0.0".equals(d.statementId())) {
                String expected = d.iteration() == 0 ? "<dv:bs[0]>" : "bs[0]";
                assertEquals(expected, d.absoluteState().toString());
            }
            if ("0.0.2.0.0".equals(d.statementId())) {
                String expected = d.iteration() == 0 ? "<dv:bs[1]>" : "bs[1]";
                assertEquals(expected, d.absoluteState().toString());
            }
            if ("0.0.3.0.0".equals(d.statementId())) {
                String expected = d.iteration() == 0 ? "<dv:bs[2]>" : "bs[2]";
                assertEquals(expected, d.absoluteState().toString());
            }
        };
        // goal is to show no errors
        testClass("DependentVariables_3", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }


    @Test
    public void test_4() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo) {
                    assertEquals("instance type int/*@Identity*/", d.currentValue().toString());
                    if ("1".equals(d.statementId())) {
                        assertEquals(DV.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
                    }
                } else if ("b".equals(d.variableName())) {
                    assertEquals("a", d.variableInfo().getValue().toString());
                } else if ("array".equals(d.variableName())) {
                    assertEquals("new int[](3)", d.currentValue().toString());
                } else if (d.variable() instanceof ReturnVariable) {
                    if ("3".equals(d.statementId())) {
                        assertEquals("12", d.currentValue().toString());
                    }
                } else if ("array[b]".equals(d.variableName())) {
                    if (d.variable() instanceof DependentVariable dv) {
                        assertEquals("b", dv.indexVariable().toString());
                    } else fail();
                    assertTrue(d.statementId().compareTo("2") >= 0);
                    assertEquals("12", d.currentValue().toString());
                } else if ("array[org.e2immu.analyser.parser.start.testexample.DependentVariables_4.method(int):0:a]".equals(d.variableName())) {
                    assertEquals("3", d.statementId());
                    assertEquals("instance type int", d.currentValue().toString());
                } else if (!(d.variable() instanceof This)) {
                    fail("This variable should not be produced: " + d.variableName() + "; statement " + d.statementId());
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method2".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    assertEquals("12", d.evaluationResult().value().toString());
                }
                if ("3".equals(d.statementId())) {
                    assertEquals("12", d.evaluationResult().value().toString());
                }
            }
        };

        // unused parameter in method1
        testClass("DependentVariables_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    // simpler version of DependentVariables_3, without the delays
    @Test
    public void test_5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("added".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("false", d.currentValue().toString());
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertEquals("a", d.currentValue().toString());
                    }
                    if ("0.0.2".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "b?<v:added>:a" : "a||b";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("0.0.3".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "b||c?<v:added>:a" : "a||b||c";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("0.0.0".equals(d.statementId())) {
                assertEquals("a||b||c||d", d.absoluteState().toString());
            }
            if ("0.0.1.0.0".equals(d.statementId())) {
                assertEquals("a", d.absoluteState().toString());
            }
            if ("0.0.2.0.0".equals(d.statementId())) {
                assertEquals("b", d.absoluteState().toString());
            }
            if ("0.0.3.0.0".equals(d.statementId())) {
                assertEquals("c", d.absoluteState().toString());
            }
        };
        testClass("DependentVariables_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }
}
