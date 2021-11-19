
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

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_00_Basics_20 extends CommonTestRunner {
    public Test_00_Basics_20() {
        super(true);
    }

    @Test
    public void test_20() throws IOException {
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
                String expectedLv = d.iteration() == 0 ? "list:-1" : "list:2";
                assertEquals(expectedLv, ci.linkedVariables().toString());
            }
            if ("getListC2".equals(d.methodInfo().name)) {
                EvaluationResult.ChangeData cd = d.findValueChangeByToString("getListC2");
                assertEquals("this.list:3", cd.linkedVariables().toString());
            }
            if ("test2".equals(d.methodInfo().name) && "4".equals(d.statementId())) {
                EvaluationResult.ChangeData ci = d.findValueChangeByToString("ci");
                String expectedLv = d.iteration() == 0 ? "list:-1" : "list:3";
                assertEquals(expectedLv, ci.linkedVariables().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("C2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "list".equals(pi.name)) {
                    assertEquals("list:0,this.list:3", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof FieldReference fr && "list".equals(fr.fieldInfo.name)) {
                    assertEquals("new ArrayList<>(list)", d.currentValue().toString());
                    assertEquals("list:3,this.list:0", d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("getFirstC1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "list".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() == 0 ? "<f:list>" : "instance type List<T>";
                    assertEquals(expectValue, d.currentValue().toString());
                    String expectLv = d.iteration() == 0 ? "return getFirstC1:-1,this.list:0" : "return getFirstC1:3,this.list:0";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = d.iteration() == 0 ? "<m:get>" : "list.get(0)";
                    assertEquals(expectValue, d.currentValue().toString());
                    String expectLv = d.iteration() == 0 ? "return getFirstC1:0,this.list:-1" : "return getFirstC1:0,this.list:3";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("getListC2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals("new ArrayList<>(list)", d.currentValue().toString());
                    assertEquals("return getListC2:0,this.list:3", d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("test1".equals(d.methodInfo().name)) {

                if ("list".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String expectValue = "new ArrayList<>()/*0==this.size()*/";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertTrue(d.currentValue() instanceof PropertyWrapper);
                        assertEquals(Level.TRUE_DV, d.getProperty(CONTAINER));
                    }
                    if ("3".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<v:list>"
                                : "instance type ArrayList<I>/*this.contains(new I())&&1==this.size()*/";
                        assertEquals(expectValue, d.currentValue().toString());

                        // i:3 gone, because substitution with "new I()"
                        String expectLv = d.iteration() <= 1 ? "i:-1,list:0" : "list:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        // delayed because linking is delayed!
                        assertDv(d, 1, Level.TRUE_DV, CONTEXT_MODIFIED);
                        assertDv(d, 1, Level.TRUE_DV, CONTAINER);
                    }
                }
                if ("ci".equals(d.variableName()) && "4".equals(d.statementId())) {
                    String expectValue = d.iteration() <= 2 ? "<new:C1<I>>" : "new C1<>(list)";
                    assertEquals(expectValue, d.currentValue().toString());

                    // delay in iteration 1 because we need to know ci's IMMUTABLE property
                    String expectLv = switch (d.iteration()) {
                        case 0, 1 -> "ci:0,i:-1,list:-1";
                        default -> "ci:0,list:2";
                    };
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    assertDv(d, 1, Level.TRUE_DV, CONTEXT_MODIFIED);
                }
                if ("ci2".equals(d.variableName()) && "5".equals(d.statementId())) {
                    String expectValue = d.iteration() <= 2 ? "<new:C1<I>>" : "new C1<>(new ArrayList<>(list))";
                    assertEquals(expectValue, d.currentValue().toString());

                    String expectLv = switch (d.iteration()) {
                        case 0, 1 -> "ci2:0,ci:-1,i:-1,list:-1";
                        default -> "ci2:0,ci:3,list:3";
                    };
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                    assertDv(d, 1, Level.FALSE_DV, CONTEXT_MODIFIED);
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("list".equals(d.fieldInfo().name) && "C1".equals(d.fieldInfo().owner.simpleName)) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 0, MultiLevel.DEPENDENT_DV, INDEPENDENT);
            }
            if ("C2".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 0, MultiLevel.INDEPENDENT_1_DV, INDEPENDENT);
            }
            if ("getFirstC1".equals(d.methodInfo().name)) {
                assertDv(d, 0, MultiLevel.INDEPENDENT_1_DV, INDEPENDENT);
            }
            if ("getFirstC2".equals(d.methodInfo().name)) {
                assertDv(d, 0, MultiLevel.INDEPENDENT_1_DV, INDEPENDENT);
            }
            if ("getListC2".equals(d.methodInfo().name)) {
                assertDv(d, 0, MultiLevel.INDEPENDENT_1_DV, INDEPENDENT);
            }
            if ("getListC1".equals(d.methodInfo().name)) {
                assertDv(d, 0, MultiLevel.DEPENDENT_DV, INDEPENDENT);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("I".equals(d.typeInfo().simpleName)) {
                assertEquals(MultiLevel.INDEPENDENT_DV, d.typeAnalysis().getProperty(INDEPENDENT));
            }
            if ("C1".equals(d.typeInfo().simpleName)) {
                assertDv(d, 0, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, IMMUTABLE);
            }
            if ("C2".equals(d.typeInfo().simpleName)) {
                assertDv(d, 0, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, IMMUTABLE);
                assertEquals(Level.TRUE_DV, d.typeAnalysis().immutableCanBeIncreasedByTypeParameters());
            }
        };

        testClass("Basics_20", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

}
