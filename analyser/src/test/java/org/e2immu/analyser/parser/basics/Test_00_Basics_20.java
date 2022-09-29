
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
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

/*
This test requires the CONTEXT_MODIFICATION assignment to a variable to be delayed when the value of the variable
is not yet known. See ComputeLinkedVariables.write and Test_16_Modification_19.
 */
public class Test_00_Basics_20 extends CommonTestRunner {
    public Test_00_Basics_20() {
        super(true);
    }

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("getFirstC1".equals(d.methodInfo().name)) {
            EvaluationResult.ChangeData cd = d.findValueChangeByToString("list");
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cd.getProperty(CONTEXT_NOT_NULL));

            EvaluationResult.ChangeData cdFirst = d.findValueChangeByToString("getFirstC1");
            String expectedLv = d.iteration() == 0 ? "this.list:-1" : "this.list:3";
            assertEquals(expectedLv, cdFirst.linkedVariables().toString());
        }
        if ("test1".equals(d.methodInfo().name) && "4".equals(d.statementId())) {
            EvaluationResult.ChangeData ci = d.findValueChangeByToString("ci");
            String expectedLv = d.iteration() <= 1 ? "list:-1" : "list:2";
            assertEquals(expectedLv, ci.linkedVariables().toString());
        }
        if ("getListC2".equals(d.methodInfo().name)) {
            EvaluationResult.ChangeData cd = d.findValueChangeByToString("getListC2");
            String expected = d.iteration() == 0 ? "this.list:-1" : "this.list:4";
            assertEquals(expected, cd.linkedVariables().toString());
        }
        if ("test2".equals(d.methodInfo().name) && "4".equals(d.statementId())) {
            EvaluationResult.ChangeData ci = d.findValueChangeByToString("ci");
            String expectedLv = d.iteration() <= 1 ? "list:-1" : "list:4";
            assertEquals(expectedLv, ci.linkedVariables().toString());
        }
    };


    StatementAnalyserVariableVisitor createVisitor(boolean expectNotNull) {
        return d -> {
            if ("C2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "list".equals(pi.name)) {
                    assertEquals("this.list:4", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof FieldReference fr && "list".equals(fr.fieldInfo.name)) {
                    assertEquals("new ArrayList<>(list)", d.currentValue().toString());
                    assertEquals("list:4", d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("getFirstC1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "list".equals(fr.fieldInfo.name)) {
                    String fieldValue = expectNotNull ? "instance type List<T>" : "nullable instance type List<T>";
                    String expectValue = d.iteration() == 0 ? "<f:list>" : fieldValue;
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = d.iteration() == 0 ? "<m:get>" : "list.get(0)";
                    assertEquals(expectValue, d.currentValue().toString());
                    String linked = d.iteration() == 0 ? "this.list:-1" : "this.list:3";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("getFirstC2".equals(d.methodInfo().name)) {
                assertEquals("C2", d.methodInfo().typeInfo.simpleName);

                if (d.variable() instanceof FieldReference fr && "list".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() == 0 ? "<f:list>" : "instance type ArrayList<T>";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = d.iteration() == 0 ? "<m:get>" : "list.get(0)";
                    assertEquals(expectValue, d.currentValue().toString());
                    String linked = d.iteration() == 0 ? "this.list:-1" : "this.list:3";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("getFirstC3".equals(d.methodInfo().name)) {
                assertEquals("C3", d.methodInfo().typeInfo.simpleName);

                if (d.variable() instanceof FieldReference fr && "list".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() == 0 ? "<f:list>" : "instance type ArrayList<Object>";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = d.iteration() == 0 ? "<m:get>" : "list.get(0)";
                    assertEquals(expectValue, d.currentValue().toString());
                    String linked = d.iteration() == 0 ? "this.list:-1" : "this.list:3";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("getFirstC4".equals(d.methodInfo().name)) {
                assertEquals("C4", d.methodInfo().typeInfo.simpleName);

                if (d.variable() instanceof FieldReference fr && "list".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() == 0 ? "<f:list>" : "instance type ArrayList<T>";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = d.iteration() == 0 ? "<m:get>" : "list.get(0)";
                    assertEquals(expectValue, d.currentValue().toString());
                    String linked = d.iteration() == 0 ? "this.list:-1" : "this.list:3";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }

            if ("getListC2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expected = d.iteration() == 0 ? "<new:ArrayList<T>>" : "new ArrayList<>(list)";
                    assertEquals(expected, d.currentValue().toString());
                    String expectedLv = d.iteration() == 0 ? "this.list:-1" : "this.list:4";
                    assertEquals(expectedLv, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("getListC3".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expected = d.iteration() == 0 ? "<new:ArrayList<Object>>" : "new ArrayList<>(list)";
                    assertEquals(expected, d.currentValue().toString());
                    String expectedLv = d.iteration() == 0 ? "this.list:-1" : "this.list:4";
                    assertEquals(expectedLv, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("getListC4".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expected = d.iteration() == 0 ? "<new:ArrayList<T>>" : "new ArrayList<>(list)";
                    assertEquals(expected, d.currentValue().toString());
                    String expectedLv = d.iteration() == 0 ? "this.list:-1" : "this.list:4";
                    assertEquals(expectedLv, d.variableInfo().getLinkedVariables().toString());
                }
            }

            if ("test1".equals(d.methodInfo().name)) {
                if ("i".equals(d.variableName())) {
                    String expected = d.iteration() <= 1 ? "<new:I>" : "instance type I";
                    if ("1".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        String lvs = d.iteration() <= 1 ? "list:-1" : "list:3";
                        // directional arrow, I'm part of the HC of the list; a change in 'I' is a change in list as well
                        assertEquals(lvs, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("list".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String expectValue = "new ArrayList<>()/*0==this.size()*/";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertTrue(d.currentValue() instanceof PropertyWrapper);
                        assertEquals(MultiLevel.CONTAINER_DV, d.getProperty(CONTAINER));
                    }
                    if ("3".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<v:list>"
                                : "instance type ArrayList<I>/*this.contains(i)&&1==this.size()*/";
                        assertEquals(expectValue, d.currentValue().toString());

                        // while class "I" is independent, it is also mutable. It is the latter property that is relevant
                        // in the "Basics_20" type
                        String lvs = d.iteration() <= 1 ? "i:-1" : "";// a change in list does not imply a change in "i"
                        // the other direction must exist!
                        assertEquals(lvs, d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, DV.TRUE_DV, CONTEXT_MODIFIED);
                        assertDv(d, 2, MultiLevel.CONTAINER_DV, CONTAINER);
                    }
                }
                if ("ci".equals(d.variableName()) && "4".equals(d.statementId())) {
                    String expectValue = d.iteration() <= 1 ? "<new:C1<I>>" : "new C1<>(list)";
                    assertEquals(expectValue, d.currentValue().toString());

                    // delay in iteration 1 because we need to know ci's IMMUTABLE property
                    String expectLv = switch (d.iteration()) {
                        case 0, 1 -> "i:-1,list:-1";
                        default -> "list:2"; // and not i:3, asymmetrical
                    };
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    assertDv(d, 2, DV.TRUE_DV, CONTEXT_MODIFIED);
                }
                if ("ci2".equals(d.variableName()) && "5".equals(d.statementId())) {
                    String expectValue = d.iteration() <= 1 ? "<new:C1<I>>" : "new C1<>(new ArrayList<>(list))";
                    assertEquals(expectValue, d.currentValue().toString());

                    String expectLv = switch (d.iteration()) {
                        case 0, 1 -> "ci:-1,i:-1,list:-1";
                        default -> "ci:4,list:4";
                    };
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                    // see explanation in method source why this is TRUE
                    assertDv(d, 2, DV.TRUE_DV, CONTEXT_MODIFIED);
                }
            }
        };
    }

    FieldAnalyserVisitor createFieldAnalyserVisitor(boolean expectNotNull) {
        DV expected = expectNotNull ? MultiLevel.EFFECTIVELY_NOT_NULL_DV : MultiLevel.NULLABLE_DV;
        return d -> {
            if ("list".equals(d.fieldInfo().name) && "C1".equals(d.fieldInfo().owner.simpleName)) {
                assertEquals(expected, d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));

                assertNull(d.haveError(Message.Label.FIELD_INITIALIZATION_NOT_NULL_CONFLICT));
            }
        };
    }

    MethodAnalyserVisitor createMethodAnalyserVisitor(boolean expectNotNull) {
        return d -> {
            if ("C1".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.DEPENDENT_DV, INDEPENDENT);
            }
            if ("C2".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_HC_DV, INDEPENDENT);
            }
            if ("getFirstC1".equals(d.methodInfo().name)) {
                assertDv(d, 1, MultiLevel.INDEPENDENT_HC_DV, INDEPENDENT);
                assertEquals(!expectNotNull && d.iteration() > 0,
                        null != d.haveError(Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION));
            }
            if ("getFirstC2".equals(d.methodInfo().name)) {
                assertDv(d, 1, MultiLevel.INDEPENDENT_HC_DV, INDEPENDENT);
            }
            if ("getListC2".equals(d.methodInfo().name)) {
                assertDv(d, 1, MultiLevel.INDEPENDENT_HC_DV, INDEPENDENT);
            }
            if ("getListC1".equals(d.methodInfo().name)) {
                assertDv(d, 1, MultiLevel.DEPENDENT_DV, INDEPENDENT);
            }
        };
    }

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if ("I".equals(d.typeInfo().simpleName)) {
            assertDv(d, MultiLevel.INDEPENDENT_DV, INDEPENDENT);
        }
        if ("C1".equals(d.typeInfo().simpleName)) {
            assertDv(d, 1, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, IMMUTABLE);
        }
        if ("C2".equals(d.typeInfo().simpleName)) {
            assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, IMMUTABLE);
            assertHc(d, 0, "T");
            assertEquals(DV.TRUE_DV, d.typeAnalysis().immutableDeterminedByTypeParameters());
        }
        if ("C3".equals(d.typeInfo().simpleName)) {
            assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, IMMUTABLE);
            assertHc(d, 0, "Object");
            assertEquals(DV.FALSE_DV, d.typeAnalysis().immutableDeterminedByTypeParameters());
        }
        if ("C4".equals(d.typeInfo().simpleName)) {
            assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, IMMUTABLE);
            assertHc(d, 0, "T");
            assertEquals(DV.TRUE_DV, d.typeAnalysis().immutableDeterminedByTypeParameters());
        }
    };

    // if we compute @NotNull over all methods, field C1.list will be @NotNull; alternatively, it will be
    // @Nullable. This is now a configuration change, which, by default, is "false"

    private void runTest(boolean expectNotNull, int warnings) throws IOException {
        testClass("Basics_20", 0, warnings, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(createVisitor(expectNotNull))
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(createFieldAnalyserVisitor(expectNotNull))
                .addAfterMethodAnalyserVisitor(createMethodAnalyserVisitor(expectNotNull))
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder()
                .setComputeContextPropertiesOverAllMethods(expectNotNull)
                .build());
    }

    @Test
    public void test_20_1() throws IOException {
        runTest(true, 0);
    }

    @Test
    public void test_20_2() throws IOException {
        // expect 4 potential null pointer warnings
        runTest(false, 4);
    }
}
