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
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class Test_12_IfStatement extends CommonTestRunner {
    public Test_12_IfStatement() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("null==a?\"b\":a", d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo p && "a".equals(p.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        // the value "null" from  equalityAccordingToState is not written because "a" is not read
                        assertEquals("nullable instance type String/*@Identity*/", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("nullable instance type String/*@Identity*/", d.currentValue().toString());
                    }
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "null==(null==a?nullable instance type String/*@Identity*/:<p:a>)?\"b\":null==a?nullable instance type String/*@Identity*/:<p:a>"
                                : "null==a?\"b\":a";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    int expected = d.iteration() == 0 ? 0 : 1;
                    assertEquals(expected, d.statementAnalysis().stateData().equalityAccordingToStateStream().count());
                }
                if ("0".equals(d.statementId())) {
                    assertEquals(0, d.statementAnalysis().stateData().equalityAccordingToStateStream().count());
                }
                // state should say "a!=null"
                if ("1".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    assertEquals("null!=a", d.state().toString());
                    assertEquals("null!=a", d.absoluteState().toString());
                    assertEquals(0, d.statementAnalysis().stateData().equalityAccordingToStateStream().count());
                }
            }
        };
        testClass("IfStatement_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("IfStatement_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("IfStatement_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("IfStatement_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    /*
    Linked variables come later in get2 and get3 as compared to get1.
    Should we be worried about this?
     */

    @Test
    public void test_4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("get3".equals(d.methodInfo().name) && "i3".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<m:get>" : "map.get(label3)";
                    assertEquals(expectValue, d.currentValue().toString());
                    String expectLv = "i3:0";
                    assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("get2".equals(d.methodInfo().name) && d.variable() instanceof This) {
                assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
            }
            if ("get1".equals(d.methodInfo().name) && d.variable() instanceof ReturnVariable) {
                String expected = d.iteration() == 0 ? "<null-check>?defaultValue1:<m:get>"
                        : "null==map.get(label1)?defaultValue1:map.get(label1)";
                assertEquals(expected, d.currentValue().toString());
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get1".equals(d.methodInfo().name) && d.iteration() > 0) {
                assertEquals("/*inline get1*/null==map.get(label1)?defaultValue1:map.get(label1)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get2".equals(d.methodInfo().name) && d.iteration() >= 2) {
                assertEquals("/*inline get2*/null==map.get(label2)?defaultValue2:map.get(label2)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get3".equals(d.methodInfo().name) && d.iteration() >= 2) {
                assertEquals("/*inline get3*/null==map.get(label3)?defaultValue3:map.get(label3)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("IfStatement_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test_5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("get1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "map".equals(fr.fieldInfo.name)) {
                    assertEquals("1", d.statementId());
                    assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                    assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    String expect = d.iteration() == 0 ? "<f:map>" : "nullable instance type Map<String,Integer>";
                    assertEquals(expect, d.currentValue().toString());
                }
            }
            if ("get3".equals(d.methodInfo().name) && "i3".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<method:java.util.Map.get(Object)>" : "map.get(label3)";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertLinked(d, 1, "?", "");
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("get1".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("\"3\".equals(label1)", d.condition().toString());
                    assertEquals("true", d.state().toString());
                    assertEquals("!\"3\".equals(label1)", d.statementAnalysis().stateData()
                            .getPrecondition().expression().toString());
                    assertEquals("!\"3\".equals(label1)", d.statementAnalysis().methodLevelData()
                            .combinedPreconditionGet().expression().toString());
                    assertTrue(d.conditionManagerForNextStatement().precondition().isEmpty());
                }
                if ("0".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    assertEquals("true", d.state().toString());
                    assertEquals("!\"3\".equals(label1)",
                            d.statementAnalysis().methodLevelData().combinedPreconditionGet().expression().toString());
                    assertTrue(d.statementAnalysis().stateData().getPrecondition().isEmpty());
                    assertTrue(d.conditionManagerForNextStatement().precondition().isEmpty());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    assertEquals("true", d.state().toString());
                    assertEquals("!\"3\".equals(label1)", d.localConditionManager()
                            .precondition().expression().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get1".equals(d.methodInfo().name) && d.iteration() > 0) {
                assertEquals("/*inline get1*/null==map.get(label1)?defaultValue1:map.get(label1)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get2".equals(d.methodInfo().name) && d.iteration() >= 2) {
                assertEquals("/*inline get2*/null==map.get(label2)?defaultValue2:map.get(label2)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get3".equals(d.methodInfo().name) && d.iteration() >= 2) {
                assertEquals("/*inline get3*/null==map.get(label3)?defaultValue3:map.get(label3)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("map".equals(d.fieldInfo().name)) {
                assertDv(d, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
            }
        };

        testClass("IfStatement_5", 0, 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_6() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<simplification>||null==set?null:<return value>" : "null";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<simplification>||<null-check>?null:<m:contains>?\"one\":\"two\"" : "???";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals(d.iteration() > 0, d.statementAnalysis().flowData().isUnreachable());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() == 0 ? "<m:method1>" : "null";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        // 4 errors, 3 in method1, 1 in method2
        testClass("IfStatement_6", 4, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test_7() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("pad".equals(d.methodInfo().name)) {
                boolean unreachable = "1".equals(d.statementId());
                assertEquals(unreachable, d.statementAnalysis().flowData().alwaysEscapesViaException());
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("pad".equals(d.methodInfo().name)) {
                assertEquals("/*inline pad*/i<=9?\"\"+i:<return value>", d.methodAnalysis().getSingleReturnValue().toString());
                assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
            }
        };
        testClass("IfStatement_7", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test_8() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("pad".equals(d.methodInfo().name)) {
                assertEquals("/*inline pad*/i<=9?\"\"+i:<return value>", d.methodAnalysis().getSingleReturnValue().toString());
                assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
            }
        };
        testClass("IfStatement_8", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    // IMPROVE for this test, it is fortunate that List.of().isEmpty() doesn't go to FALSE
    @Test
    public void test_9() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("targetIsATypeParameter".equals(d.methodInfo().name)) {
                if ("5".equals(d.statementId())) {
                    Optional<EvaluationResult.ChangeData> cd = d.evaluationResult().changeData().entrySet().stream()
                            .filter(e -> e.getKey().simpleName().equals("fromTypeBounds"))
                            .map(Map.Entry::getValue).findAny();
                    assertTrue(cd.isEmpty(), "Got: " + cd);
                }
                if ("4.0.1".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<m:isEmpty>" : "List.of().isEmpty()";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("targetIsATypeParameter".equals(d.methodInfo().name)) {
                if ("4.0.1.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<m:isEmpty>" : "List.of().isEmpty()";
                    assertEquals(expected, d.condition().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("targetIsATypeParameter".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("<return value>", d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:isEmpty>?<s:int>:<return value>" : "List.of().isEmpty()?5:<return value>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("3".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<null-check>?<s:int>:<m:isEmpty>?<s:int>:<return value>"
                                : "null==from.typeInfo$0?List.of().isEmpty()?5:<return value>:6";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("4.0.1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:isEmpty>?7:<return value>" : "<return value>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("4.0.4".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<m:isEmpty>?7:8+(<loopIsNotEmptyCondition>&&<loopIsNotEmptyCondition>&&<v:min>><m:size>?<m:size>:<vl:min>)";
                            case 1 -> "8+(targetTypeBounds$4.0.3.isEmpty()?instance type int:fromTypeBounds$4.0.3.0.0.isEmpty()?instance type int:min$4.0.3.0.0><m:size>?<m:size>:<vl:min>)";
                            default -> "8+(targetTypeBounds$4.0.3.isEmpty()?instance type int:fromTypeBounds$4.0.3.0.0.isEmpty()||`otherBound.typeInfo`.length()>=min$4.0.3.0.0?instance type int:`otherBound.typeInfo`.length())";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("fromTypeBounds".equals(d.variableName())) {
                    assertNotEquals("4", d.statementId(), "Variable should not exist here!");
                    assertNotEquals("5", d.statementId(), "Variable should not exist here!");
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.NormalLocalVariable lv) {
                        assertEquals("4", lv.parentBlockIndex);
                        if ("4.0.4".equals(d.statementId())) {
                            String expected = d.iteration() == 0 ? "<vl:fromTypeBounds>" : "List.of()";
                            assertEquals(expected, d.currentValue().toString());
                        }
                    } else if (d.variableInfoContainer().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outside) {
                        if (d.statementId().startsWith("4.0.3.0.0")) {
                            assertEquals("4.0.3.0.0", outside.statementIndex(), "In " + d.statementId());
                        } else {
                            assertEquals("4.0.3", outside.statementIndex(), "In " + d.statementId());
                            assertTrue(outside.previousVariableNature() instanceof VariableNature.NormalLocalVariable);
                            assertTrue(d.statementId().startsWith("4.0.3"));
                        }
                    } else fail();
                }
            }
        };
        testClass("IfStatement_9", 6, 2, new DebugConfiguration.Builder()
       //         .addEvaluationResultVisitor(evaluationResultVisitor)
          //      .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
          //      .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    // quasi identical to test_9, but with "private" on the typeParameter field
    @Test
    public void test_10() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("targetIsATypeParameter".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<null-check>" : "false";
                    assertEquals(expected, d.state().toString());
                    DV dv = d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock();
                    String delay = d.iteration() == 0 ? "initial_flow_value@Method_targetIsATypeParameter_1-C" : "NEVER:0";
                    assertEquals(delay, dv.toString());
                    assertEquals(d.iteration() == 0, dv.isDelayed());
                }
            }
        };
        testClass("IfStatement_10", 6, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    // simpler version of DependentVariables_3, without the delays
    @Test
    public void test_11() throws IOException {
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
                        assertEquals("a||b", d.currentValue().toString());
                    }
                    if ("0.0.3".equals(d.statementId())) {
                        assertEquals("a||b||c", d.currentValue().toString());
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
        testClass("IfStatement_11", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }
}
