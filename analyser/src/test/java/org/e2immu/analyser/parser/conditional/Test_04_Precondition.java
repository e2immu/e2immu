
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

package org.e2immu.analyser.parser.conditional;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.Negation;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.conditional.testexample.Precondition_4;
import org.e2immu.analyser.parser.conditional.testexample.Precondition_6;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
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
                            d.statementAnalysis().stateData().getConditionManagerForNextStatement().condition().toString());
                    assertEquals("null!=e1||null!=e2",
                            d.statementAnalysis().stateData().getPrecondition().expression().toString());
                }
                if ("0".equals(d.statementId())) {
                    assertEquals("true",
                            d.statementAnalysis().stateData().getConditionManagerForNextStatement().state().toString());
                    assertTrue(d.statementAnalysis().stateData().getPrecondition().isEmpty());
                    assertEquals("null!=e1||null!=e2",
                            d.statementAnalysis().methodLevelData().combinedPreconditionGet().expression().toString());
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("either".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                assertEquals("null==e1&&null==e2", d.evaluationResult().value().toString());
            }
            if ("useEither3".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                String expected = d.iteration() == 0 ? "<m:either>" : "Precondition_0.either(f1,f2)";
                assertEquals(expected, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("useEither3".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ParameterInfo pi && "f1".equals(pi.name)) {
                    assertDv(d, 1, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String name = d.methodInfo().name;
            if ("either".equals(name)) {
                MethodAnalysis methodAnalysis = d.methodAnalysis();
                assertEquals("null!=e1||null!=e2", methodAnalysis.getPrecondition().expression().toString());
                String expected = d.iteration() == 0 ? "<m:either>" : "e1+e2";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d.p(0), 1, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                assertDv(d.p(0), 1, MultiLevel.NULLABLE_DV, NOT_NULL_PARAMETER);
            }
            if ("useEither3".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                assertDv(d.p(0), 2, MultiLevel.NULLABLE_DV, NOT_NULL_PARAMETER);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> assertEquals(4, d.typeInfo().typeInspection.get().methods().size());

        testClass("Precondition_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    // positive
    @Test
    public void test1() throws IOException {

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setPositive1".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals(d.iteration() == 0, d.localConditionManager().isDelayed());
                    assertEquals(d.iteration() > 0, d.statementAnalysis().stateData().preconditionIsFinal());
                    assertEquals(d.iteration() > 0, d.statementAnalysis().methodLevelData().combinedPreconditionIsFinal());

                    if (d.iteration() > 0) {
                        assertEquals("i$0<0", d.condition().toString());
                        assertEquals("i>=0", d.statementAnalysis().stateData()
                                .getPrecondition().expression().toString());
                        assertEquals("i>=0", d.statementAnalysis().methodLevelData()
                                .combinedPreconditionGet().expression().toString());
                    }
                }
                if ("0".equals(d.statementId())) {
                    assertTrue(d.condition().isBoolValueTrue());
                    assertTrue(d.state().isBoolValueTrue());
                    if (d.iteration() >= 3) {
                        assertEquals("i>=0", d.statementAnalysis().methodLevelData()
                                .combinedPreconditionGet().expression().toString());
                    }
                }
                if ("0".equals(d.statementId())) {
                    assertEquals(d.iteration() > 0, d.statementAnalysis().methodLevelData().combinedPreconditionIsFinal());
                    if (d.iteration() > 0) {
                        assertEquals("i>=0", d.statementAnalysis().methodLevelData()
                                .combinedPreconditionGet().expression().toString());
                    }
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(d.iteration() == 0, d.localConditionManager().isDelayed());
                    if (d.iteration() > 0) {
                        assertEquals("i>=0", d.localConditionManager().precondition().expression().toString());
                    }
                }
            }
            if ("setPositive2".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    if (d.iteration() > 2) {
                        assertEquals("j1>=0", d.localConditionManager().precondition().expression().toString());
                    }
                }
            }
            if ("setPositive5".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String pc = d.iteration() == 0 ? "Precondition[expression=<f:i>>=0, causes=[escape]]"
                            : "Precondition[expression=i>=0, causes=[escape]]";
                    assertEquals(pc, d.statementAnalysis().methodLevelData().combinedPreconditionGet().toString());
                }
                if ("1".equals(d.statementId())) {
                    String pc = d.iteration() == 0 ? "Precondition[expression=<precondition>, causes=[]]"
                            : "Precondition[expression=i>=0, causes=[escape]]";
                    assertEquals(pc, d.localConditionManager().precondition().toString());
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("setPositive5".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    // TODO I'd expect only "j2<0" here in iteration 1; somehow i$0>=0 is not filtered out
                    String expect = d.iteration() == 0 ? "<f:i>>=0&&j2<0" : "i$0>=0&&j2<0";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
            }
        };

        testClass("Precondition_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }


    @Test
    public void test1_1() throws IOException {
        testClass("Precondition_1_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test1_2() throws IOException {
        testClass("Precondition_1_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test1_3() throws IOException {
        testClass("Precondition_1_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test1_34() throws IOException {
        testClass("Precondition_1_34", 0, 0, new DebugConfiguration.Builder()
                        .build(),
                new AnalyserConfiguration.Builder().setForceExtraDelayForTesting(true).build());
    }

    @Test
    public void test1_4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setPositive4".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "j3".equals(p.name)) {
                    String expected = d.iteration() == 0 ? "<p:j3>" : "instance type int/*@Identity*/";
                    if ("0".equals(d.statementId())) {
                        assertEquals("instance type int/*@Identity*/", d.currentValue().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fieldReference && "i".equals(fieldReference.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<s:int>";
                            case 1 -> "<wrapped:i>";
                            default -> "j3";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setPositive4".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("CM{parent=CM{}}", d.conditionManagerForNextStatement().toString());
                }
            }
        };
        testClass("Precondition_1_4", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setForceExtraDelayForTesting(true).build());
    }

    @Test
    public void test1_5() throws IOException {
        testClass("Precondition_1_5", 0, 0, new DebugConfiguration.Builder()
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
    // note that most of the code is inside a synchronization block, where statement time stands still:
    // variable fields act as local variables
    @Test
    public void test3() throws IOException {
        final String TYPE = "org.e2immu.analyser.parser.conditional.testexample.Precondition_3";
        final String INTEGER = TYPE + ".integer";
        final String RETURN_VAR = TYPE + ".setInteger(int)";

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("integer".equals(d.fieldInfo().name)) {
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
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
                        String expectValue = switch (d.iteration()) {
                            case 0 -> "<f:integer>";
                            case 1 -> "<wrapped:integer>";
                            default -> "nullable instance type Integer";
                        };
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
                            assertEquals("<s:Integer>", d.currentValue().toString());
                        } else {
                            assertEquals("ii", d.currentValue().toString());
                        }
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, IMMUTABLE);
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
                    assertTrue(d.statementAnalysis().inSyncBlock());
                    assertEquals("true", d.state().toString());
                    assertEquals("ii>=0", d.statementAnalysis().methodLevelData()
                            .combinedPreconditionGet().expression().toString());
                }
                if ("0.0.0.0.0".equals(d.statementId())) {
                    assertTrue(d.statementAnalysis().inSyncBlock());
                    assertEquals("ii<0", d.condition().toString());
                }
                if ("0.0.1.0.0".equals(d.statementId())) {
                    assertTrue(d.statementAnalysis().inSyncBlock());
                    String expect = conditionIn0(d.iteration());
                    assertEquals(expect, d.condition().toString());
                }
                if ("0.0.2".equals(d.statementId())) {
                    assertEquals(d.iteration() <= 1, d.localConditionManager().isDelayed());

                    assertTrue(d.statementAnalysis().variableIsSet(INTEGER));
                    VariableInfo variableInfo = d.getFieldAsVariable(integer);
                    assertTrue(variableInfo.isAssigned());

                    if (d.iteration() <= 1) {
                        assertFalse(d.statementAnalysis().methodLevelData().combinedPreconditionIsFinal());
                    } else {
                        assertTrue(d.statementAnalysis().methodLevelData().combinedPreconditionIsFinal());
                        assertEquals("null==integer&&ii>=0",
                                d.statementAnalysis().methodLevelData().combinedPreconditionGet().expression().toString());
                    }
                }
                if ("0".equals(d.statementId())) {
                    // the synchronized block
                    assertTrue(d.statementAnalysis().variableIsSet(INTEGER));
                    VariableInfo variableInfo = d.getFieldAsVariable(integer);
                    assertTrue(variableInfo.isAssigned());

                    String expect = notConditionIn0(d.iteration());
                    assertEquals(expect, d.statementAnalysis().methodLevelData()
                            .combinedPreconditionGet().expression().toString());
                    assertEquals(d.iteration() > 1,
                            d.statementAnalysis().methodLevelData().combinedPreconditionIsFinal());
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
            case 0, 1 -> "!<null-check>";
            default -> "null!=integer";
        };
    }

    private static String notConditionIn0(int iteration) {
        return switch (iteration) {
            case 0, 1 -> "<null-check>&&ii>=0";
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
                    assertEquals("i<0&&b", d.statementAnalysis().stateData()
                            .getConditionManagerForNextStatement().absoluteState(d.context()).toString());

                    assertEquals("i>=0||!b", d.statementAnalysis()
                            .stateData().getPrecondition().expression().toString());
                }
                if ("0".equals(d.statementId())) {
                    // has moved to the precondition

                    assertTrue(d.statementAnalysis().stateData()
                            .getConditionManagerForNextStatement().precondition().isEmpty());
                    assertEquals("i>=0||!b", d.statementAnalysis()
                            .methodLevelData().combinedPreconditionGet().expression().toString());
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
        assertEquals("return i>=0||!b;", methodAnalysis.getComputedCompanions().values()
                .stream().findFirst().orElseThrow()
                .methodInspection.get().getMethodBody().structure.statements().get(0).minimalOutput());
    }


    @Test
    public void test_7() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("pop".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "stack".equals(fr.fieldInfo.name)) {
                    assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("pop".equals(d.methodInfo().name)) {
                String expected = switch (d.iteration()) {
                    case 0 -> "Precondition[expression=<precondition>, causes=[escape]]";
                    default -> "Precondition[expression=true, causes=[]]";
                };
                assertEquals(expected, d.methodAnalysis().getPrecondition().toString());
                if (d.iteration() >= 1) assertTrue(d.methodAnalysis().getPrecondition().isEmpty());
            }
        };
        testClass("Precondition_7", 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build());
    }


    @Test
    public void test_8() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                assertEquals("Precondition[expression=!\"escape\".equals(in), causes=[escape]]",
                        d.methodAnalysis().getPrecondition().toString());
            }
        };
        testClass("Precondition_8", 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build());
    }


    /*
    the not-null restriction on 'in' is either in the precondition, or on the parameter, but it is definitely present!!
     */
    @Test
    public void test9() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                String pre = "Precondition[expression=null!=in&&!in.isEmpty()&&Character.isUpperCase(in.charAt(0)), causes=[escape, escape]]";
                assertEquals(pre, d.methodAnalysis().getPrecondition().toString());
                assertTrue(d.methodAnalysis().getPostConditions().isEmpty(),
                        "Got: " + d.methodAnalysis().getPostConditions().toString());
                assertDv(d.p(0), 1, MultiLevel.NULLABLE_DV, NOT_NULL_PARAMETER);
            }
            if ("method2".equals(d.methodInfo().name)) {
                String finalValue = "Precondition[expression=!in.isEmpty()&&!Character.isUpperCase(in.charAt(0)), causes=[escape, escape]]";
                if (d.methodAnalysis().preconditionStatus().isDone()) {
                    assertEquals(finalValue, d.methodAnalysis().getPrecondition().toString());
                }
                assertTrue(d.methodAnalysis().getPostConditions().isEmpty(),
                        "Got: " + d.methodAnalysis().getPostConditions().toString());
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_PARAMETER);
            }
            if ("method3".equals(d.methodInfo().name)) {
                String finalValue = "Precondition[expression=!in.isEmpty()&&!Character.isUpperCase(in.charAt(0)), causes=[escape]]";
                if (d.methodAnalysis().preconditionStatus().isDone()) {
                    assertEquals(finalValue, d.methodAnalysis().getPrecondition().toString());
                    And and = d.methodAnalysis().getPrecondition().expression().asInstanceOf(And.class);
                    assertTrue(Identifier.acceptIdentifier(and.getIdentifier()));
                    MethodCall mc = and.getExpressions().get(0).asInstanceOf(Negation.class).expression.asInstanceOf(MethodCall.class);
                    assertTrue(Identifier.acceptIdentifier(mc.getIdentifier()));
                    VariableExpression ve = mc.object.asInstanceOf(VariableExpression.class);
                    assertTrue(ve.getIdentifier() instanceof Identifier.PositionalIdentifier);
                }
                assertTrue(d.methodAnalysis().getPostConditions().isEmpty(),
                        "Got: " + d.methodAnalysis().getPostConditions().toString());
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_PARAMETER);
            }
        };
        testClass("Precondition_9", 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build());
    }

    @Test
    public void test_10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("npe".equals(d.variableName())) {
                    if ("5.0.0".equals(d.statementId())) {
                        String expected = d.iteration() < 2 ? "<s:NullPointerException>"
                                : "Precondition_10.tryCatch(supplier).exception()/*(NullPointerException)*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 1 ? "Precondition[expression=<null-check>, causes=[escape, escape]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(expected,
                        d.methodAnalysis().getPrecondition().toString());
            }
        };
        testClass("Precondition_10", 0, 0,
                new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build());
    }


    /*
    we want the throws statement to be absorbed in an empty precondition.
     */
    @Test
    public void test_11() throws IOException {

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                assertTrue(d.methodAnalysis().indicesOfEscapesNotInPreOrPostConditions().isEmpty());
                String expected = "Precondition[expression=a, causes=[escape]]"; // FIXME should be empty
                assertEquals(expected, d.methodAnalysis().getPrecondition().toString());
            }
        };
        // expect one error: if(b) evaluates to constant
        testClass("Precondition_11", 1, 0,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build());
    }

}
