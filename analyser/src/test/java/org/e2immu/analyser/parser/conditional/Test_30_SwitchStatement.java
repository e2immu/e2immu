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

import org.e2immu.analyser.analyser.ConditionManager;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.ForwardAnalysisInfo;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.impl.FlowDataImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_30_SwitchStatement extends CommonTestRunner {
    public Test_30_SwitchStatement() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String value = d.currentValue().toString();
                    if ("0.0.0".equals(d.statementId())) {
                        //    assertEquals("'a'==c?\"a\":<default>", value);
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        //     assertEquals("\"a\"", value);
                    }
                    if ("0.0.2".equals(d.statementId())) {
                        //     assertEquals("\"a\"", value);
                    }
                    if ("0".equals(d.statementId())) {
                        //      assertEquals("", value);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("'a'==c", d.condition().toString());
                    assertNotNull(d.forwardAnalysisInfo().switchData());
                }
                if ("0.0.1".equals(d.statementId())) {
                    assertEquals("'b'==c", d.condition().toString());
                }
                if ("0.0.2".equals(d.statementId())) {
                    assertEquals("'a'!=c&&'b'!=c", d.condition().toString());
                }
            }
        };
        testClass("SwitchStatement_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("SwitchStatement_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                String condition = d.evaluationResult().evaluationContext().getConditionManager().condition().toString();
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("'a'==c", condition);
                    assertEquals("\"a\"", d.evaluationResult().value().toString());
                }
                if ("0.0.1".equals(d.statementId())) {
                    /*
                    Explanation of "true" rather than "'b'==c": because we already have a return value ("a") the
                    code follows the second path in SAEvaluationOfMainExpression.createAndEvaluateReturnStatement:
                    the condition is moved into the expression.
                    */
                    assertEquals("'b'==c", condition);
                    assertEquals("\"b\"", d.evaluationResult().value().toString());
                }
                if ("0.0.2".equals(d.statementId())) {
                    // this point comes before we check the value against the condition manager
                    assertEquals("'a'!=c&&'b'!=c", condition);
                    assertEquals("'a'==c||'b'==c", d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "0.0.2".equals(d.statementId())) {
                assertNotNull(d.haveError(Message.Label.CONDITION_EVALUATES_TO_CONSTANT));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
        };

        testClass("SwitchStatement_2", 2, 1, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:method>" : "/*inline method*/b";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("SwitchStatement_3", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.statementId().startsWith("1.")) {
                    assertEquals("{}", d.statementAnalysis().flowData().interruptsFlowGet().toString());
                }
            }
        };
        testClass("SwitchStatement_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_5() throws IOException {
        testClass("SwitchStatement_5", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_6() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "3".equals(d.statementId()) && "res".equals(d.variableName())) {
                assertEquals("instance 2 type String", d.currentValue().toString());
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    assertNotNull(d.haveError(Message.Label.TRIVIAL_CASES_IN_SWITCH));
                    assertEquals("{}", d.statementAnalysis().flowData().getInterruptsFlow().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.CONSTANT);
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
            }
        };

        testClass("SwitchStatement_6", 1, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_7() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("values".equals(d.methodInfo().name) && "Choices".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = switch (d.iteration()) {
                        case 0 -> "{<f:ONE>,<f:TWO>,<f:THREE>,<f:FOUR>}";
                        case 1 ->
                                "{<vp:ONE:container@Enum_Choices>,<vp:TWO:container@Enum_Choices>,<vp:THREE:container@Enum_Choices>,<vp:FOUR:container@Enum_Choices>}";
                        default -> "{Choices.ONE,Choices.TWO,Choices.THREE,Choices.FOUR}";
                    };
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals(d.iteration() > 1, d.variableInfo().valueIsSet());
                    assertDv(d, 2, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    if (d.iteration() > 1) {
                        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, d.currentValue()
                                .getProperty(d.context(), Property.NOT_NULL_EXPRESSION, true));
                    }
                }
            }
        };
        testClass("SwitchStatement_7", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test_8() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("s".equals(d.variableName())) {
                    if ("1.0.00".equals(d.statementId())) {
                        assertEquals("\"u\"", d.currentValue().toString());
                    }
                    if ("1.0.03".equals(d.statementId())) {
                        assertEquals("b?\"x\":\"u\"", d.currentValue().toString());
                    }
                    if ("1.0.04".equals(d.statementId())) {
                        assertEquals("b?\"x\":\"u\"", d.currentValue().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        // FIXME
                        assertEquals("\"x?\"", d.currentValue().toString());
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        assertEquals("\"y\"", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("instance 1 type String", d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.00".equals(d.statementId())) {
                    assertEquals("2==i", d.absoluteState().toString());
                }
                if ("1.0.01".equals(d.statementId())) {
                    assertEquals("2==i", d.absoluteState().toString());
                }
                if ("1.0.03.0.0".equals(d.statementId())) {
                    assertEquals("3==i&&b", d.absoluteState().toString());
                }
                if ("1.0.04.0.0".equals(d.statementId())) {
                    assertEquals("c&&(3==i||4==i)&&(4==i||!b)", d.absoluteState().toString());
                }
                if ("1.0.05".equals(d.statementId())) {
                    assertEquals("(3==i||4==i||5==i)&&(4==i||5==i||!b)&&(5==i||!c)", d.absoluteState().toString());
                }
                if ("1.0.07".equals(d.statementId())) {
                    assertEquals("6==i", d.absoluteState().toString());
                }
                if ("1.0.09".equals(d.statementId())) {
                    assertEquals("2!=i&&3!=i&&4!=i&&5!=i&&6!=i", d.absoluteState().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                assertEquals("s$1", d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("--", d.delaySequence());

        testClass("SwitchStatement_8", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }


    @Test
    public void test_9() throws IOException {

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0.0.0.0.0".equals(d.statementId())) {
                    assertEquals("3==i&&b&&c", d.absoluteState().toString());
                }
                if ("1.0.0.0.0".equals(d.statementId())) {
                    ForwardAnalysisInfo.SwitchData switchData = d.forwardAnalysisInfo().switchData();
                    assertNotNull(switchData);
                    assertEquals("b", switchData.initialConditionManager().absoluteState(d.context()).toString());
                    ConditionManager cm = d.statementAnalysis().stateData().getConditionManagerForNextStatement();
                    assertEquals("3==i&&b&&!c", cm.absoluteState(d.context()).toString());
                    assertEquals("b", cm.parent().condition().toString());
                    assertEquals("3==i", cm.condition().toString());
                }
                if ("1.0.0.0.1.0.0".equals(d.statementId())) {
                    assertEquals("b&&(3==i||4==i)&&(4==i||!c)", d.absoluteState().toString());
                }
                if ("1.0.0.0.1".equals(d.statementId())) {
                    ForwardAnalysisInfo.SwitchData switchData = d.forwardAnalysisInfo().switchData();
                    assertNotNull(switchData);
                    assertEquals("b", switchData.initialConditionManager().absoluteState(d.context()).toString());
                    ConditionManager cm = d.statementAnalysis().stateData().getConditionManagerForNextStatement();
                    assertEquals("b", cm.parent().condition().toString());
                    assertEquals("(3==i||4==i)&&(4==i||!c)", cm.condition().toString());
                    assertEquals("true", cm.state().toString());
                    assertEquals("b&&(3==i||4==i)&&(4==i||!c)", cm.absoluteState(d.context()).toString());
                }
                if ("1.0.0.0.2".equals(d.statementId())) {
                    ForwardAnalysisInfo.SwitchData switchData = d.forwardAnalysisInfo().switchData();
                    assertNotNull(switchData);
                    assertEquals("b", switchData.initialConditionManager().absoluteState(d.context()).toString());
                    ConditionManager cm = d.statementAnalysis().stateData().getConditionManagerForNextStatement();
                    assertEquals("b", cm.parent().condition().toString());
                    assertEquals("5==i||6==i", cm.condition().toString());
                    assertEquals("true", cm.state().toString());
                    assertEquals("b&&(5==i||6==i)", cm.absoluteState(d.context()).toString());
                }
                if ("1.0.0.0.4".equals(d.statementId())) {
                    ForwardAnalysisInfo.SwitchData switchData = d.forwardAnalysisInfo().switchData();
                    assertNotNull(switchData);
                    assertEquals("b", switchData.initialConditionManager().absoluteState(d.context()).toString());
                    ConditionManager cm = d.statementAnalysis().stateData().getConditionManagerForNextStatement();
                    assertEquals("b", cm.parent().condition().toString());
                    assertEquals("3!=i&&4!=i&&5!=i&&6!=i", cm.condition().toString());
                    assertEquals("true", cm.state().toString());
                    assertEquals("3!=i&&4!=i&&5!=i&&6!=i&&b", cm.absoluteState(d.context()).toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                // FIXME nullable is wrong
                String expected = "b?nullable instance 1.0.0 type String:\"s\"";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("--", d.delaySequence());

        testClass("SwitchStatement_9", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

}
