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

package org.e2immu.analyser.parser.start;

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Precondition;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.analysis.impl.ValueAndPropertyProxy;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class Test_46_Singleton extends CommonTestRunner {

    public Test_46_Singleton() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        testClass("Singleton_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    /*
    EventuallyE2Immutable_10 is a non-constructor version of Singleton_1.
     */
    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Singleton_1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "created".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertCurrentValue(d, 2, "instance type boolean");
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Singleton_1".equals(d.methodInfo().name)) {
                Precondition precondition = d.methodAnalysis().getPrecondition();
                String expected = switch (d.iteration()) {
                    case 0 -> "!<f:created>";
                    case 1 -> "!<f*:created>";
                    default -> "!Singleton_1.created";
                };
                assertEquals(expected, precondition.expression().toString());
                CausesOfDelay causesOfDelay = d.methodAnalysis().preconditionStatus();
                assertEquals(d.iteration() <= 1, causesOfDelay.isDelayed());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("created".equals(d.fieldInfo().name)) {
                String expected = d.iteration() <= 1 ? "<variable value>" : "[true,false]";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
                assertDv(d, DV.FALSE_DV, Property.FINAL);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Singleton_1".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, DV.TRUE_DV, Property.SINGLETON);
            }
        };

        testClass("Singleton_1", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setForceExtraDelayForTesting(true).build());
    }

    @Test
    public void test_2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                String delays = switch (d.iteration()) {
                    case 0 -> "initial:SingletonClass.SINGLETON@Method_test_0-C";
                    case 1 -> "container@Class_SingletonClass";
                    case 2 -> "initial:this.k@Method_multiply_0-C;srv@Method_multiply";
                    default -> "";
                };
                assertEquals(delays, d.evaluationResult().causesOfDelay().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            assertFalse(d.context().evaluationContext().allowBreakDelay());
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("SINGLETON".equals(d.fieldInfo().name)) {
                assertEquals("SingletonClass", d.fieldInfo().owner.simpleName);
                assertEquals("new SingletonClass(2)", d.fieldAnalysis().getValue().toString());
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("multiply".equals(d.methodInfo().name)) {
                String expect = d.iteration() <= 2 ? "<m:multiply>" : "/*inline multiply*/k*i";
                assertEquals(expect, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass("Singleton_2", 0, 0, new DebugConfiguration.Builder()
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setForceExtraDelayForTesting(true).build());
    }

    // counter-example to the technique of test 0
    @Test
    public void test_3() throws IOException {
        testClass("Singleton_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // counter-example to the technique of test 0
    @Test
    public void test_4() throws IOException {
        testClass("Singleton_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // counter-example to the technique of test 1
    @Test
    public void test_5() throws IOException {
        testClass("Singleton_5", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // counter-example to the technique of test 1
    @Test
    public void test_6() throws IOException {
        testClass("Singleton_6", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // counter-example to the technique of test 1
    @Test
    public void test_7() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Singleton_7".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "created".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertCurrentValue(d, 2, "instance type boolean");
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<s:boolean>";
                            case 1 -> "<wrapped:created>";
                            default -> "false";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("Singleton_7".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "!<f:created>";
                        case 1 -> "!<f*:created>";
                        default -> "!Singleton_7.created";
                    };
                    assertEquals(expected,
                            d.statementAnalysis().stateData().getPrecondition().expression().toString());
                    String expected1 = switch (d.iteration()) {
                        case 0 -> "<f:created>";
                        case 1 -> "<f*:created>";
                        default -> "Singleton_7.created";
                    };
                    assertEquals(expected1,
                            d.statementAnalysis().stateData().getConditionManagerForNextStatement().condition().toString());
                }
                if ("0".equals(d.statementId())) {
                    assertEquals(d.iteration() > 1, d.statementAnalysis().methodLevelData().combinedPreconditionIsFinal());
                    String expected = switch (d.iteration()) {
                        case 0 -> "!<f:created>";
                        case 1 -> "!<f*:created>";
                        default -> "!Singleton_7.created";
                    };
                    assertEquals(expected,
                            d.statementAnalysis().methodLevelData().combinedPreconditionGet().expression().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("created".equals(d.fieldInfo().name)) {
                FieldAnalysisImpl.Builder builder = (FieldAnalysisImpl.Builder) d.fieldAnalysis();
                String expected = d.iteration() <= 1 ? "<variable value>" : "false";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());
                if (d.iteration() > 0) {
                    assertEquals("[false, false]", builder.getValues().stream()
                            .map(ValueAndPropertyProxy::getValue).toList().toString());
                }
            }
        };

        testClass("Singleton_7", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setForceExtraDelayForTesting(true).build());
    }

    // counter-example to the technique of test 1
    @Test
    public void test_8() throws IOException {
        testClass("Singleton_8", 1, 0, new DebugConfiguration.Builder()
                .build());
    }
}
