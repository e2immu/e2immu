
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

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_26_Enum extends CommonTestRunner {

    public Test_26_Enum() {
        super(false);
    }

    /*
    synthetically generated:
    one field per enum constant, methods "name", "valueOf", "values"
     */
    @Test
    public void test0() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("values".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = d.iteration() == 0 ? "{<f:ONE>,<f:TWO>,<f:THREE>}" : "{ONE,TWO,THREE}";
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("values".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().methodInspection.get().isSynthetic());
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("{ONE,TWO,THREE}", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("ONE".equals(d.fieldInfo().name)) {
                assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                assertEquals("new Enum_0()", d.fieldAnalysis().getEffectivelyFinalValue().toString());
            }
        };

        testClass("Enum_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("posInList".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "this==<v:<m:values>[<v:i>]>" : "instance type Enum_1==this";
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                }
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<delayed array length>><v:i>" : "i$0<=2";
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() == 0, d.evaluationResult().someValueWasDelayed());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("posInList".equals(d.methodInfo().name)) {
                if ("Enum_1.values()[i]".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "this==<new:Enum_1>?<v:Enum_1.values()[i]>:<new:Enum_1>" :
                                "instance type Enum_1";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                    }
                    if ("0".equals(d.statementId())) {
                        if (d.iteration() > 0) {
                            assertNotSame(LinkedVariables.DELAY,
                                    d.variableInfoContainer().best(VariableInfoContainer.Level.EVALUATION).getLinkedVariables());
                        }
                        assertTrue(d.variableInfo().getLinkedVariables().isEmpty());

                        assertEquals(MultiLevel.NOT_INVOLVED, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
                        assertEquals(MultiLevel.NOT_INVOLVED, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                        assertEquals(MultiLevel.MUTABLE, d.getProperty(VariableProperty.CONTEXT_IMMUTABLE));
                        assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("posInList".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                }
            }
        };

        testClass("Enum_1", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test2() throws IOException {
        testClass("Enum_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test3() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Enum_3";
        final String ONE = TYPE + ".THREE";
        final String TWO = TYPE + ".TWO";
        final String THREE = TYPE + ".THREE";
        final String THIS = TYPE + ".this";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"posInList".equals(d.methodInfo().name)) return;
            if ("array".equals(d.variableName()) && ("0".equals(d.statementId()) || "1".equals(d.statementId()))) {
                String expectValue = d.iteration() == 0 ? "<m:values>" : "{ONE,TWO,THREE}";
                assertEquals(expectValue, d.currentValue().toString());
            }
            if ("array[i]".equals(d.variableName())) {
                if ("2.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "this==<new:Enum_3>?<v:array[i]>:<new:Enum_3>" : "instance type Enum_3";
                    assertEquals(expectValue, d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<v:array[i]>" : "instance type Enum_3";
                    assertEquals(expectValue, d.currentValue().toString());
                }
            }
            if (THIS.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    assertEquals("instance type Enum_3", d.currentValue().toString());
                }
            }
            if (THREE.equals(d.variableName())) {
                if ("0".equals(d.statementId()) || "1".equals(d.statementId()) || "2".equals(d.statementId())) {
                    assertEquals("new Enum_3(3)", d.currentValue().toString());
                    //      assertTrue(d.iteration() >= 3);
                }
            }
            if ("i$2".equals(d.variableName())) {
                if ("2.0.0.0.0".equals(d.statementId())) {
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    assertEquals("", d.variableInfo().getStaticallyAssignedVariables().toString());
                }
            }
            if ("i$2$2-E".equals(d.variableName())) {
                if ("2.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<v:i$2$2-E>" : "1+i$2";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("posInList".equals(d.methodInfo().name)) { // starting from statement 0, they'll all have to be there
                assertEquals(d.iteration() > 0, d.statementAnalysis().variables.isSet(ONE));
                assertEquals(d.iteration() > 0, d.statementAnalysis().variables.isSet(TWO));
                assertEquals(d.iteration() > 0, d.statementAnalysis().variables.isSet(THREE));

                if ("2.0.0.0.0".equals(d.statementId())) {
                    String expectCondition = d.iteration() == 0 ? "this==<v:array[<v:i>]>" : "instance type Enum_3==this";
                    assertEquals(expectCondition, d.condition().toString());
                }

                if ("2.0.0".equals(d.statementId())) {
                    assertEquals(d.iteration() > 0, d.statementAnalysis().variables.isSet("array[i]"));
                    String expectCondition = d.iteration() == 0 ? "<delayed array length>><v:i>" : "i$2<=2";
                    assertEquals(expectCondition, d.condition().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("highest".equals(d.methodInfo().name)) {
                int expectConstant = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectConstant, d.methodAnalysis().getProperty(VariableProperty.CONSTANT));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("THREE".equals(d.fieldInfo().name)) {
                assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.CONSTANT));
            }
            if ("cnt".equals(d.fieldInfo().name)) {
                assertEquals("cnt", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };


        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            int expectImmutable = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
        };

        // expect an "always true" warning on the assert
        testClass("Enum_3", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test4() throws IOException {
        // two assert statements should return "true"
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("cnt".equals(d.fieldInfo().name)) {
                assertEquals("cnt", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                assertTrue(d.fieldAnalysis().getEffectivelyFinalValue() instanceof VariableExpression ve
                        && ve.variable() instanceof ParameterInfo);
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Enum_4".equals(d.methodInfo().name)) {
                ParameterAnalysis cnt = d.parameterAnalyses().get(0);
                if (d.iteration() > 0) {
                    assertTrue(cnt.assignedToFieldIsFrozen());
                    assertEquals("{cnt=ASSIGNED}", cnt.getAssignedToField().toString());
                }
            }
        };

        testClass("Enum_4", 0, 2, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test5() throws IOException {
        testClass("Enum_5", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    /*
    without API, there'll be a conflict
     */
    @Test
    public void test6() throws IOException {
        testClass("Enum_6", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
