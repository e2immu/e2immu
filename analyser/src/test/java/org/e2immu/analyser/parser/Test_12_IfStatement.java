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
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.variable.This;
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
        testClass("IfStatement_0", 0, 0, new DebugConfiguration.Builder()
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
    public void test4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("get3".equals(d.methodInfo().name) && "i3".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<m:get>" : "map.get(label3)";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("get2".equals(d.methodInfo().name) && d.variable() instanceof This) {
                int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get1".equals(d.methodInfo().name) && d.iteration() > 0) {
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
    public void test5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("get3".equals(d.methodInfo().name) && "i3".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<method:java.util.Map.get(Object)>" : "map.get(label3)";
                    assertEquals(expectValue, d.currentValue().toString());
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("get1".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("\"3\".equals(label1)", d.condition().toString());
                    assertEquals("true", d.state().toString());
                    assertEquals("!\"3\".equals(label1)", d.statementAnalysis().stateData
                            .getPrecondition().expression().toString());
                    assertEquals("!\"3\".equals(label1)", d.statementAnalysis().methodLevelData
                            .combinedPrecondition.get().expression().toString());
                    assertTrue(d.conditionManagerForNextStatement().precondition().isEmpty());
                }
                if ("0".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    assertEquals("true", d.state().toString());
                    assertEquals("!\"3\".equals(label1)",
                            d.statementAnalysis().methodLevelData.combinedPrecondition.get().expression().toString());
                    assertTrue(d.statementAnalysis().stateData.getPrecondition().isEmpty());
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

        testClass("IfStatement_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_6() throws IOException {
        // 4 errors, 3 in method1, 1 in method2
        testClass("IfStatement_6", 4, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_7() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("pad".equals(d.methodInfo().name)) {
                boolean unreachable = "1".equals(d.statementId());
                assertEquals(unreachable, d.statementAnalysis().flowData.alwaysEscapesViaException());
            }
        };

        testClass("IfStatement_7", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }
}
