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
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                                ? "null==(null==nullable instance type String/*@Identity*/?nullable instance type String/*@Identity*/:<p:a>)?\"b\":null==nullable instance type String/*@Identity*/?nullable instance type String/*@Identity*/:<p:a>"
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
                    String linked = d.iteration() == 0 ? "label3:-1,this.map:-1,this:-1" : "this:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("get2".equals(d.methodInfo().name) && d.variable() instanceof This) {
                if ("0".equals(d.statementId())) {
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
            if ("get1".equals(d.methodInfo().name) && d.variable() instanceof ReturnVariable) {
                String expected = d.iteration() == 0
                        ? "<null-check>?defaultValue1:<m:get>"
                        : "null==map.get(label1)?defaultValue1:map.get(label1)";
                assertEquals(expected, d.currentValue().toString());
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get1".equals(d.methodInfo().name) && d.iteration() >= 2) {
                assertEquals("null==map.get(label1)?defaultValue1:map.get(label1)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get2".equals(d.methodInfo().name) && d.iteration() >= 2) {
                assertEquals("null==map.get(label2)?defaultValue2:map.get(label2)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get3".equals(d.methodInfo().name) && d.iteration() >= 2) {
                assertEquals("null==map.get(label3)?defaultValue3:map.get(label3)",
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
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
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
            if ("get1".equals(d.methodInfo().name) && d.iteration() >= 2) {
                assertEquals("null==map.get(label1)?defaultValue1:map.get(label1)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get2".equals(d.methodInfo().name) && d.iteration() >= 2) {
                assertEquals("null==map.get(label2)?defaultValue2:map.get(label2)",
                        d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get3".equals(d.methodInfo().name) && d.iteration() >= 2) {
                assertEquals("null==map.get(label3)?defaultValue3:map.get(label3)",
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
                        String expected = d.iteration() == 0 ? "null==set||<simplification>?null:<return value>" : "null";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1, 2, 3 -> "<null-check>||<simplification>?null:<m:contains>?\"one\":\"two\"";
                            default -> "???";
                        };
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
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
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
                assertEquals("i<10?\"\"+i:<return value>", d.methodAnalysis().getSingleReturnValue().toString());
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
                assertEquals("i<10?\"\"+i:<return value>", d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass("IfStatement_8", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_9() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("expensiveCall".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("null==in?null:<return value>", d.currentValue().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("\"empty\"", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("null==in?null:in.isEmpty()?\"empty\":<return value>",
                                d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("null==in?null:in.isEmpty()?\"empty\":Character.isAlphabetic(in.charAt(0))?in:\"non-alpha\"",
                                d.currentValue().toString());
                    }
                }
            }
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<null-check>?null:<return value>" : "null==IfStatement_9.expensiveCall(in)?null:<return value>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("expensiveCall".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("null!=in", d.state().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("in.isEmpty()", d.condition().toString());
                    /* IMPORTANT: the order is wrong from a shortcut operator point of view, but the And class will sort
                      the clauses first thing.
                     */
                    assertEquals("null!=in&&in.isEmpty()", d.absoluteState().toString());
                }
                if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                    assertEquals("null!=in&&!in.isEmpty()", d.state().toString());
                }
            }
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String state = d.iteration() == 0 ? "!<null-check>" : "null!=IfStatement_9.expensiveCall(in)";
                    assertEquals(state, d.state().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("expensiveCall".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:expensiveCall>"
                        : "null==in?null:in.isEmpty()?\"empty\":Character.isAlphabetic(in.charAt(0))?in:\"non-alpha\"";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("method".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 2 ? "<m:method>"
                        : "null==IfStatement_9.expensiveCall(in)?null:\"Not null: \"+IfStatement_9.expensiveCall(in)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass("IfStatement_9", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }
}
