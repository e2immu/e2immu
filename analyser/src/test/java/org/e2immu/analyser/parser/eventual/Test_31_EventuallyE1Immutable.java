
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

package org.e2immu.analyser.parser.eventual;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_31_EventuallyE1Immutable extends CommonTestRunner {

    public Test_31_EventuallyE1Immutable() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        final String TYPE = "org.e2immu.analyser.parser.eventual.testexample.EventuallyE1Immutable_0";
        final String STRING = TYPE + ".string";
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("setString".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    assertTrue(d.haveMarkRead(STRING));
                    VariableInfoContainer stringVic = d.statementAnalysis().getVariable(STRING);
                    assertEquals(DV.FALSE_DV, stringVic.getPreviousOrInitial().getProperty(Property.CONTEXT_MODIFIED));
                    assertTrue(stringVic.hasEvaluation());
                    assertFalse(stringVic.hasMerge());
                    assertEquals("", stringVic.getPreviousOrInitial().getLinkedVariables().toString());
                    assertEquals("", stringVic.current().getLinkedVariables().toString());
                    assertEquals(DV.FALSE_DV, stringVic.current().getProperty(Property.CONTEXT_MODIFIED));
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setString".equals(d.methodInfo().name) || "setString2".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId()) && d.variable() instanceof FieldReference fr && "string".equals(fr.fieldInfo.name)) {
                    assertNotEquals(MultiLevel.NULLABLE_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                }
            }
            if ("setString".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "string".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1".equals(d.statementId())) {
                        // must be it 1, break init delay!
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertTrue(d.variableInfoContainer().hasMerge());
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "string".equals(fr.fieldInfo.name)) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "initial@Field_string";
                        case 1 -> "bid_merge@Method_setString_0:M";
                        default -> "";
                    };
                    if ("0".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().causesOfDelay().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().causesOfDelay().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expect = switch (d.iteration()) {
                            case 0 -> "<s:String>";
                            case 1 -> "<wrapped:string>";
                            default -> "string";
                        };
                        assertEquals(expect, d.currentValue().toString());
                        String delay = switch (d.iteration()) {
                            case 0 -> "initial:this.string@Method_setString_0-C;state:this.string@Method_setString_2-E";
                            case 1 -> "break_init_delay:this.string@Method_setString_0-C";
                            default -> "";
                        };
                        assertEquals(delay, d.currentValue().causesOfDelay().toString());
                    }
                }
            }
            if ("setString2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "string".equals(p.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
        };

        // setString, @NotNull on the parameter
        // show what's happening at statement 1, where CNN is a bit tricky because of the previous statement that
        // generates a precondition. Because it is a null condition on a parameter, it does not end up in a precondition.
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setString".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "<c:boolean>";
                        case 1 -> "<s:boolean>";
                        default -> "null==string";
                    };
                    assertEquals(expected,
                            d.statementAnalysis().stateData().getConditionManagerForNextStatement().condition().toString());
                    if (d.iteration() > 1) {
                        assertEquals("true", d.statementAnalysis().stateData().getPrecondition().expression().toString());
                    }
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("true",
                            d.statementAnalysis().stateData().getConditionManagerForNextStatement().state().toString());
                    assertTrue(d.statementAnalysis().stateData().getPrecondition().isEmpty());

                    //the combined precondition only shows the one generated by the previous statement
                    String expected = switch (d.iteration()) {
                        case 0 -> "!<c:boolean>&&!<null-check>";
                        case 1 -> "!<s:boolean>&&!<null-check>";
                        default -> "null==string";
                    };
                    assertEquals(expected,
                            d.statementAnalysis().methodLevelData().combinedPreconditionGet().expression().toString());
                }
            }
            // the situation precondition-wise should be the same in setString2, but then without the delays
            if ("setString2".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("null==string2",
                            d.statementAnalysis().stateData().getConditionManagerForNextStatement().condition().toString());
                    String expected = d.iteration() == 0 ? "<precondition>" : "true";
                    assertEquals(expected, d.statementAnalysis().stateData().getPrecondition().expression().toString());
                }
                if ("0".equals(d.statementId())) {
                    assertEquals("true",
                            d.statementAnalysis().stateData().getConditionManagerForNextStatement().state().toString());
                    assertTrue(d.statementAnalysis().stateData().getPrecondition().isEmpty());
                    String expected = d.iteration() == 0 ? "<precondition>" : "true";
                    assertEquals(expected,
                            d.statementAnalysis().methodLevelData().combinedPreconditionGet().expression().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setString".equals(d.methodInfo().name)) {
                String expected = switch (d.iteration()) {
                    case 0 -> "<precondition>";
                    case 1 -> "!<s:boolean>&&!<null-check>";
                    default -> "null==string";
                };
                assertEquals(expected, d.methodAnalysis().getPreconditionForEventual().expression().toString());
                if (d.iteration() > 3) {
                    MethodAnalysis.Eventual eventual = d.methodAnalysis().getEventual();
                    assertNotNull(eventual);
                    assertEquals("string", eventual.markLabel());
                }
            }
            if ("setString2".equals(d.methodInfo().name)) {
                String expected = switch (d.iteration()) {
                    case 0 -> "<precondition>";
                    case 1 -> "!<null-check>";
                    default -> "null==string";
                };
                assertEquals(expected, d.methodAnalysis().getPreconditionForEventual().expression().toString());
                if (d.iteration() > 3) {
                    MethodAnalysis.Eventual eventual = d.methodAnalysis().getEventual();
                    assertNotNull(eventual);
                    assertEquals("string", eventual.markLabel());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyE1Immutable_0".equals(d.typeInfo().simpleName)) {
                int expectSize = d.iteration() <= 1 ? 0 : 1;
                assertEquals(expectSize, d.typeAnalysis().getApprovedPreconditionsE1().size());

                assertDv(d, 2, MultiLevel.EVENTUALLY_E1IMMUTABLE_DV, Property.IMMUTABLE);

                String expectFields = d.iteration() <= 1 ? "" : "string";
                assertEquals(expectFields, d.typeAnalysis().marksRequiredForImmutable().stream()
                        .map(f -> f.name).collect(Collectors.joining()));
                assertEquals(expectFields, d.typeAnalysis().markLabel());
                // direct conditions on fields, no fields of eventually immutable type
                assertTrue(d.typeAnalysis().getEventuallyImmutableFields().isEmpty());
            }
        };

        testClass("EventuallyE1Immutable_0", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("EventuallyE1Immutable_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("addIfGreater".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<return value>||i>=<f:j>";
                            case 1 -> "<return value>||i>=<wrapped:j>";
                            default -> "<return value>||i>=j$0";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "i>=<f:j>";
                            case 1 -> "<wrapped:j>";
                            default -> "i>=j$0";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, DV.FALSE_DV, Property.IDENTITY);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "i".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("instance type int/*@Identity*/", d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "j".equals(fr.fieldInfo.name)) {
                    assertNotNull(fr.scopeVariable);
                    assertEquals("this", fr.scopeVariable.simpleName());
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<f:j>";
                            case 1 -> "<wrapped:j>";
                            default -> "instance type int";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("addIfGreater".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "CM{state=-1-i+<f:j>>=0;pc=Precondition[expression=<precondition>, causes=[]];parent=CM{}}";
                        case 1 -> "CM{state=-1-i+<wrapped:j>>=0;pc=Precondition[expression=<precondition>, causes=[]];parent=CM{}}";
                        default -> "CM{state=-1-i+j$0>=0;pc=Precondition[expression=j>=1, causes=[escape]];parent=CM{}}";
                    };
                    assertEquals(expected,
                            d.statementAnalysis().stateData().getConditionManagerForNextStatement().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setNegativeJ".equals(d.methodInfo().name)) {
                String expected = switch (d.iteration()) {
                    case 0 -> "Precondition[expression=<precondition>, causes=[]]";
                    case 1 -> "Precondition[expression=j<=0&&<f*:j><=0, causes=[escape, escape]]";
                    default -> "Precondition[expression=j<=0, causes=[escape, escape]]";
                };
                assertEquals(expected, d.methodAnalysis().getPreconditionForEventual().toString());
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyE1Immutable_2_M".equals(d.typeInfo().simpleName)) {
                String expectApprovedPreconditions = d.iteration() <= 1 ? "{}" : "{j=j<=0}";
                assertEquals(expectApprovedPreconditions, d.typeAnalysis().getApprovedPreconditionsE1().toString());
                assertEquals(expectApprovedPreconditions, d.typeAnalysis().getApprovedPreconditionsE2().toString());
            }
        };

        testClass("EventuallyE1Immutable_2_M", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setForceExtraDelayForTesting(true).build());
    }

    @Test
    public void test_3() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyE1Immutable_3".equals(d.typeInfo().simpleName)) {
                String expectE1 = d.iteration() <= 1 ? "{}" : "{string=null==string}";
                assertEquals(expectE1, d.typeAnalysis().getApprovedPreconditionsE1().toString());
                String expectE2 = d.iteration() <= 1 ? "{}" : "{string=null==string}";
                assertEquals(expectE2, d.typeAnalysis().getApprovedPreconditionsE2().toString());
                assertDv(d, 2, MultiLevel.EVENTUALLY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        testClass("EventuallyE1Immutable_3", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }
}
