
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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.testexample.Precondition_4;
import org.e2immu.analyser.testexample.Precondition_6;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.IMMUTABLE;
import static org.e2immu.analyser.analyser.Property.NOT_NULL_EXPRESSION;
import static org.junit.jupiter.api.Assertions.*;

public class Test_04_Precondition extends CommonTestRunner {

    public Test_04_Precondition() {
        super(true);
    }

    // either
    @Test
    public void test0() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("either".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("null==e1&&null==e2",
                            d.statementAnalysis().stateData().conditionManagerForNextStatement.get().condition().toString());
                    assertEquals("null!=e1||null!=e2",
                            d.statementAnalysis().stateData().getPrecondition().expression().toString());
                }
                if ("0".equals(d.statementId())) {
                    assertEquals("true",
                            d.statementAnalysis().stateData().conditionManagerForNextStatement.get().state().toString());
                    assertTrue(d.statementAnalysis().stateData().getPrecondition().isEmpty());
                    assertEquals("null!=e1||null!=e2",
                            d.statementAnalysis().methodLevelData().combinedPrecondition.get().expression().toString());
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("either".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                assertEquals("null==e1&&null==e2", d.evaluationResult().value().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String name = d.methodInfo().name;
            if ("either".equals(name)) {
                MethodAnalysis methodAnalysis = d.methodAnalysis();
                assertEquals("null!=e1||null!=e2", methodAnalysis.getPrecondition().expression().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> assertEquals(4, d.typeInfo().typeInspection.get().methods().size());

        testClass("Precondition_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    // positive
    @Test
    public void test1() throws IOException {

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setPositive1".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertTrue(d.localConditionManager().isDelayed());
                        assertFalse(d.statementAnalysis().stateData().preconditionIsFinal());
                        assertTrue(d.statementAnalysis().methodLevelData().combinedPrecondition.isVariable());
                    } else if (d.iteration() == 1) {
                        assertEquals("i$0<=-1", d.condition().toString());
                        assertEquals("i>=0", d.statementAnalysis().stateData()
                                .getPrecondition().expression().toString());
                        assertEquals("i>=0", d.statementAnalysis().methodLevelData()
                                .combinedPrecondition.get().expression().toString());
                    }
                }
                if ("0".equals(d.statementId())) {
                    assertTrue(d.condition().isBoolValueTrue());
                    assertTrue(d.state().isBoolValueTrue());
                    if (d.iteration() > 0) {
                        assertEquals("i>=0", d.statementAnalysis().methodLevelData()
                                .combinedPrecondition.get().expression().toString());
                    }
                }
                if ("0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertTrue(d.statementAnalysis().methodLevelData().combinedPrecondition.isVariable());
                    } else {
                        assertEquals("i>=0", d.statementAnalysis().methodLevelData()
                                .combinedPrecondition.get().expression().toString());
                    }
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(d.iteration() == 0, d.localConditionManager().isDelayed());
                }
            }
        };

        testClass("Precondition_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    // combined
    @Test
    public void test2() throws IOException {
        testClass("Precondition_2", 1, 0, new DebugConfiguration.Builder()
                .build());
    }

    // integer
    // Example of breaking a state delay in SAApply, maybeValueNeedsState
    @Test
    public void test3() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Precondition_3";
        final String INTEGER = TYPE + ".integer";
        final String RETURN_VAR = TYPE + ".setInteger(int)";

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("integer".equals(d.fieldInfo().name)) {
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                String expectedDelay = "initial:this.integer@Method_setInteger_0.0.1;state:this.integer@Method_setInteger_0.0.2";
                assertDv(d, expectedDelay, 1, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String name = d.methodInfo().name;
            if ("setInteger".equals(name)) {
                String expectPrecondition = notConditionIn0(d.iteration());
                assertEquals(expectPrecondition, d.methodAnalysis().getPrecondition().expression().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setInteger".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "integer".equals(fr.fieldInfo.name)) {
                    assertEquals(INTEGER, d.variableName());
                    if ("0.0.1.0.0".equals(d.statementId())) {
                        assertTrue(d.variableInfo().isRead());
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertTrue(d.variableInfo().isRead());
                        String expectValue = d.iteration() <= 1 ? "<f:integer>" : "nullable instance type Integer";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("0.0.2".equals(d.statementId())) {
                        assertTrue(d.variableInfo().isRead());
                        assertTrue(d.variableInfo().isAssigned());
                    }
                    if ("1".equals(d.statementId())) {
                        assertTrue(d.variableInfo().isRead());
                        assertTrue(d.variableInfo().isAssigned());
                    }
                }

                if (d.variable() instanceof ReturnVariable) {
                    assertEquals(RETURN_VAR, d.variableName());
                    if ("1".equals(d.statementId())) {
                        if (d.iteration() <= 1) {
                            // not <s:Integer> because both state and value are delayed; preference to value (see EvaluationResult.assignment)
                            assertEquals("<p:ii>>=0?<p:ii>:null", d.currentValue().toString());
                        } else {
                            assertEquals("ii", d.currentValue().toString());
                        }
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
                    }
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("setInteger".equals(d.methodInfo().name)) {
                if ("0.0.1".equals(d.statementId())) {
                    String expect = conditionIn0(d.iteration());
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("0.0.2".equals(d.statementId())) {
                    String expect = "ii";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("1".equals(d.statementId())) {
                    String expect = d.iteration() <= 1 ? "<p:ii>>=0?<p:ii>:null" : "ii";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setInteger".equals(d.methodInfo().name)) {
                FieldInfo integer = d.methodInfo().typeInfo.getFieldByName("integer", true);
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("true", d.state().toString());
                    assertEquals("ii>=0", d.statementAnalysis().methodLevelData()
                            .combinedPrecondition.get().expression().toString());
                }
                if ("0.0.0.0.0".equals(d.statementId())) {
                    assertEquals("ii<=-1", d.condition().toString());
                }
                if ("0.0.1.0.0".equals(d.statementId())) {
                    String expect = conditionIn0(d.iteration());
                    assertEquals(expect, d.condition().toString());
                }
                if ("0.0.2".equals(d.statementId())) {
                    assertEquals(d.iteration() <= 1, d.localConditionManager().isDelayed());

                    assertTrue(d.statementAnalysis().variableIsSet(INTEGER));
                    VariableInfo variableInfo = d.getFieldAsVariable(integer);
                    assertTrue(variableInfo.isAssigned());

                    if (d.iteration() <= 1) {
                        assertFalse(d.statementAnalysis().methodLevelData().combinedPrecondition.isFinal());
                    } else {
                        assertTrue(d.statementAnalysis().methodLevelData().combinedPrecondition.isFinal());
                        assertEquals("null==integer&&ii>=0",
                                d.statementAnalysis().methodLevelData().combinedPrecondition.get().expression().toString());
                    }
                }
                if ("0".equals(d.statementId())) {
                    // the synchronized block
                    assertTrue(d.statementAnalysis().variableIsSet(INTEGER));
                    VariableInfo variableInfo = d.getFieldAsVariable(integer);
                    assertTrue(variableInfo.isAssigned());

                    String expect = notConditionIn0(d.iteration());
                    assertEquals(expect, d.statementAnalysis().methodLevelData()
                            .combinedPrecondition.get().expression().toString());
                    assertEquals(d.iteration() <= 1,
                            d.statementAnalysis().methodLevelData().combinedPrecondition.isVariable());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(d.iteration() <= 1, d.localConditionManager().isDelayed());

                    assertTrue(d.statementAnalysis().variableIsSet(INTEGER));
                    VariableInfo variableInfo = d.getFieldAsVariable(integer);
                    assertTrue(variableInfo.isAssigned());

                    if (d.iteration() > 1) {
                        assertNotNull(d.haveError(Message.Label.INLINE_CONDITION_EVALUATES_TO_CONSTANT));
                    }
                }
            }
        };

        testClass("Precondition_3", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    private static String conditionIn0(int iteration) {
        return switch (iteration) {
            case 0 -> "null!=<f:integer>";
            case 1 -> "null!=<vp:java.lang.Integer:initial:this.integer@Method_setInteger_0.0.1;state:this.integer@Method_setInteger_0.0.2>";
            default -> "null!=integer$0";
        };
    }

    private static String notConditionIn0(int iteration) {
        return switch (iteration) {
            case 0 -> "null==<f:integer>&&ii>=0";
            case 1 -> "null==<vp:java.lang.Integer:initial:this.integer@Method_setInteger_0.0.1;state:this.integer@Method_setInteger_0.0.2>&&ii>=0";
            default -> "null==integer&&ii>=0";
        };
    }


    @Test
    public void test4() throws IOException {
        TypeContext typeContext = testClass("Precondition_4", 0, 0, new DebugConfiguration.Builder()
                .build());
        TypeInfo pc4 = typeContext.getFullyQualified(Precondition_4.class);
        MethodInfo test = pc4.findUniqueMethod("test", 1);

        MethodAnalysis methodAnalysis = test.methodAnalysis.get();
        assertEquals(1, methodAnalysis.getComputedCompanions().size());
        assertEquals("return !strings.contains(\"a\");", methodAnalysis.getComputedCompanions().values()
                .stream().findFirst().orElseThrow()
                .methodInspection.get().getMethodBody().structure.statements().get(0).minimalOutput());
    }

    /* IMPROVE FOR LATER: code not yet present to detect this
    @Test
    public void test_5() throws IOException {
        TypeContext typeContext = testClass("Precondition_5", 0, 0, new DebugConfiguration.Builder()
                .build());
        TypeInfo pc4 = typeContext.getFullyQualified(Precondition_5.class);
        MethodInfo setPositive1 = pc4.findUniqueMethod("setPositive1", 1);

        MethodAnalysis methodAnalysis = setPositive1.methodAnalysis.get();
        assertEquals(1, methodAnalysis.getComputedCompanions().size());
        assertEquals("return !strings.contains(\"a\");", methodAnalysis.getComputedCompanions().values()
                .stream().findFirst().orElseThrow()
                .methodInspection.get().getMethodBody().structure.statements().get(0).minimalOutput());
    }
    */

    @Test
    public void test6() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setI".equals(d.methodInfo().name)) {
                if ("0.0.0.0.0".equals(d.statementId())) {
                    assertEquals("b&&i<=-1", d.statementAnalysis().stateData()
                            .conditionManagerForNextStatement.get().absoluteState(d.evaluationContext()).toString());

                    assertEquals("!b||i>=0", d.statementAnalysis()
                            .stateData().getPrecondition().expression().toString());
                }
                if ("0".equals(d.statementId())) {
                    // has moved to the precondition

                    assertTrue(d.statementAnalysis().stateData()
                            .conditionManagerForNextStatement.get().precondition().isEmpty());
                    assertEquals("!b||i>=0", d.statementAnalysis()
                            .methodLevelData().combinedPrecondition.get().expression().toString());
                }
            }
        };
        TypeContext typeContext = testClass("Precondition_6", 0, 0,
                new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .build());
        TypeInfo typeInfo = typeContext.getFullyQualified(Precondition_6.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("setI", 2);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();

        assertEquals(1, methodAnalysis.getComputedCompanions().size());
        assertEquals("return !b||i>=0;", methodAnalysis.getComputedCompanions().values()
                .stream().findFirst().orElseThrow()
                .methodInspection.get().getMethodBody().structure.statements().get(0).minimalOutput());
    }
}
