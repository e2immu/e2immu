
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
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

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
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, cd.getProperty(VariableProperty.CONTEXT_NOT_NULL));

                EvaluationResult.ChangeData cdFirst = d.findValueChangeByToString("getFirstC1");
                String expectedLv = d.iteration() == 0 ? "this.list:-1" : "this.list:3";
                assertEquals(expectedLv, cdFirst.linkedVariables().toString());
            }
            if ("test1".equals(d.methodInfo().name) && "4".equals(d.statementId())) {
                EvaluationResult.ChangeData ci = d.findValueChangeByToString("ci");
                String expectedLv = d.iteration() <= 2 ? "list:-1" : "list:2";
                assertEquals(expectedLv, ci.linkedVariables().toString());
            }
            if ("getListC2".equals(d.methodInfo().name)) {
                EvaluationResult.ChangeData cd = d.findValueChangeByToString("getListC2");
                String expectLv = d.iteration() == 0 ? "this.list:-1" : "this.list:3";
                assertEquals(expectLv, cd.linkedVariables().toString());
            }
            if ("test2".equals(d.methodInfo().name) && "4".equals(d.statementId())) {
                EvaluationResult.ChangeData ci = d.findValueChangeByToString("ci");
                String expectedLv = d.iteration() <= 1 ? "list:-1" : "list:3";
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
                    String expectValue = d.iteration() == 0 ? "<new:ArrayList<T>>" : "new ArrayList<>(list)";
                    assertEquals(expectValue, d.currentValue().toString());
                    String expectLv = d.iteration() == 0 ? "return getListC2:0,this.list:-1" : "return getListC2:0,this.list:3";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("test1".equals(d.methodInfo().name)) {

                if ("list".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String expectValue = "new ArrayList<>()/*0==this.size()*/";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1 ? "<v:list>"
                                : "instance type ArrayList<I>/*this.contains(i)&&1==this.size()*/";
                        assertEquals(expectValue, d.currentValue().toString());

                        String expectLv = d.iteration() <= 1 ? "i:-1,list:0" : "i:3,list:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                        // delayed because linking is delayed!
                        int expectCm = d.iteration()<= 1? Level.DELAY: Level.TRUE;
                        assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
                if ("ci".equals(d.variableName()) && "4".equals(d.statementId())) {
                    String expectValue = d.iteration() <= 2 ? "<new:C1<I>>" : "new C1<>(list)";
                    assertEquals(expectValue, d.currentValue().toString());

                    // delay in iteration 1 because we need to know ci's IMMUTABLE property
                    String expectLv = d.iteration() <= 2 ? "ci:0,i:-1,list:-1" : "ci:0,i:3,list:2";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                    int expectCm = d.iteration() <= 2 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
                if ("ci2".equals(d.variableName()) && "5".equals(d.statementId())) {
                    String expectValue = d.iteration() <= 2 ? "<new:C1<I>>" : "new C1<>(new ArrayList<>(list))";
                    assertEquals(expectValue, d.currentValue().toString());

                    String expectLv = d.iteration() <= 2 ? "ci2:0,ci:-1,i:-1,list:-1" : "ci2:0,ci:3,i:3,list:3";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());

                    int expectCm = d.iteration() <= 2 ? Level.DELAY : Level.FALSE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("list".equals(d.fieldInfo().name) && "C1".equals(d.fieldInfo().owner.simpleName)) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.DEPENDENT;
                assertEquals(expectIndependent, p0.getProperty(VariableProperty.INDEPENDENT));
            }
            if ("C2".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.INDEPENDENT_1;
                assertEquals(expectIndependent, p0.getProperty(VariableProperty.INDEPENDENT));
            }
            if ("getFirstC1".equals(d.methodInfo().name)) {
                int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.INDEPENDENT_1;
                assertEquals(expectIndependent, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));
            }
            if ("getFirstC2".equals(d.methodInfo().name)) {
                int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.INDEPENDENT_1;
                assertEquals(expectIndependent, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));
            }
            if ("getListC2".equals(d.methodInfo().name)) {
                int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.INDEPENDENT_1;
                assertEquals(expectIndependent, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));
            }
            if ("getListC1".equals(d.methodInfo().name)) {
                int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.DEPENDENT;
                assertEquals(expectIndependent, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("I".equals(d.typeInfo().simpleName)) {
                int expectIndependent = MultiLevel.INDEPENDENT;
                assertEquals(expectIndependent, d.typeAnalysis().getProperty(VariableProperty.INDEPENDENT));
            }
            if ("C1".equals(d.typeInfo().simpleName)) {
                int expectImmutable = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE;
                assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
            if ("C2".equals(d.typeInfo().simpleName)) {
                int expectImmutable = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));

                assertTrue(d.typeAnalysis().immutableCanBeIncreasedByTypeParameters());
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
