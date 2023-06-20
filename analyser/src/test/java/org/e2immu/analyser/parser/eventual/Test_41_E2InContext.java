
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

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_41_E2InContext extends CommonTestRunner {

    public Test_41_E2InContext() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Eventually".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EVENTUALLY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("error".equals(d.methodInfo().name)) {
                if ("eventually".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() < 3 ? "<new:Eventually<String>>" : "new Eventually<>()";
                        assertEquals(expect, d.currentValue().toString());
                        assertDv(d, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_IMMUTABLE);
                        assertDv(d, 3, MultiLevel.EVENTUALLY_IMMUTABLE_BEFORE_MARK_DV, Property.IMMUTABLE);
                        assertDv(d, MultiLevel.MUTABLE_DV, Property.CONTEXT_IMMUTABLE);
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() < 3 ? "<new:Eventually<String>>" : "instance type Eventually<String>";
                        assertEquals(expect, d.currentValue().toString());
                        // so while the instance has value property ERE, the change from ConstructorCall to Instance does not change the value properties
                        assertDv(d, 3, MultiLevel.EVENTUALLY_IMMUTABLE_BEFORE_MARK_DV, Property.IMMUTABLE);
                        // the change is reflected in the CONTEXT_IMMUTABLE
                        assertDv(d, 3, MultiLevel.EVENTUALLY_IMMUTABLE_AFTER_MARK_DV, Property.CONTEXT_IMMUTABLE);
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("error".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    assertEquals(d.iteration() <= 2, null == d.haveError(Message.Label.EVENTUAL_BEFORE_REQUIRED));
                    mustSeeIteration(d, 3);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("notYetSet".equals(d.methodInfo().name)) {
                assertDv(d, 3, MultiLevel.EVENTUALLY_IMMUTABLE_BEFORE_MARK_DV, Property.IMMUTABLE);
            }
            if ("error".equals(d.methodInfo().name)) {
                assertDv(d, 3, MultiLevel.EVENTUALLY_IMMUTABLE_AFTER_MARK_DV, Property.IMMUTABLE);
            }
        };

        // error expected in the "error" method
        testClass("E2InContext_0", 1, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("eventually".equals(d.fieldInfo().name)) {
                String expected = d.iteration() <= 2 ? "<f:eventually>" : "instance type Eventually<String>";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());

                // value is corrected because of exposure via getEventually(); see FieldAnalyserImpl.correctForExposureBefore
                assertDv(d, 3, MultiLevel.EVENTUALLY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("getEventually".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "eventually".equals(fr.fieldInfo.name)) {
                    assertDv(d, 4, MultiLevel.EVENTUALLY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                    String expectValue = d.iteration() <= 3 ? "<f:eventually>" : "instance type Eventually<String>";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals("this.eventually:0", d.variableInfo().getLinkedVariables().toString());
                    assertDv(d, 4, MultiLevel.EVENTUALLY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                    assertDv(d, 4, MultiLevel.EVENTUALLY_IMMUTABLE_DV, Property.IMMUTABLE);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getEventually".equals(d.methodInfo().name)) {
                assertDv(d, 4, MultiLevel.EVENTUALLY_IMMUTABLE_DV, Property.IMMUTABLE);
                // INDEPENDENT follows the "after" state
                assertDv(d, 4, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
            if ("set".equals(d.methodInfo().name)) {
                MethodAnalysis.Eventual eventual = d.methodAnalysis().getEventual();
                if (d.iteration() <= 2) {
                    assertTrue(eventual.causesOfDelay().isDelayed());
                } else {
                    assertEquals("@Mark: [t]", eventual.toString());
                }
            }
        };

        testClass("E2InContext_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("E2InContext_2".equals(d.methodInfo().name)) {
                assert "0".equals(d.statementId());
                if (d.variable() instanceof FieldReference fr && "eventually".equals(fr.fieldInfo.name)) {
                    assertEquals("this", fr.scope.toString());
                    assertDv(d, 3, MultiLevel.EVENTUALLY_IMMUTABLE_BEFORE_MARK_DV, Property.IMMUTABLE);
                    // from iteration 3, set is @Mark
                    String delay = switch (d.iteration()) {
                        case 0 -> "initial@Method_E2InContext_2_0-C";
                        case 1 -> "cm@Parameter_t;constructor-to-instance@Method_E2InContext_2_0-E;mom@Parameter_t";
                        case 2 -> "break_init_delay:this.t@Method_set_1-C;constructor-to-instance@Method_E2InContext_2_0-E";
                        default -> "";
                    };
                    assertDv(d, delay, 3, MultiLevel.EVENTUALLY_IMMUTABLE_AFTER_MARK_DV, Property.CONTEXT_IMMUTABLE);

                    String expected = d.iteration() <= 2 ? "<f:eventually>" : "instance type Eventually<String>";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
                String values = d.iteration() == 0
                        ? "initial:this.t@Method_set_1-C;no precondition info@Method_set_0.0.0-C;state:this.t@Method_set_2-E;values:this.t@Field_t"
                        : "";
                assertEquals(values, d.fieldAnalysis().valuesDelayed().toString());
            }
            if ("eventually".equals(d.fieldInfo().name)) {
                String immDelay = switch (d.iteration()) {
                    case 0 -> "immutable@Class_Eventually";
                    case 1 -> "final@Field_t"; // haven't seen t yet
                    case 2 -> "break_init_delay:this.t@Method_set_1-C"; // working towards values of t
                    default -> "";
                };
                assertDv(d, immDelay, 3, MultiLevel.EVENTUALLY_IMMUTABLE_AFTER_MARK_DV, Property.EXTERNAL_IMMUTABLE);
                String expected = d.iteration() <= 2 ? "<f:eventually>" : "instance type Eventually<String>";
                assertEquals(expected, d.fieldAnalysis().getValue().toString());

                String delay = d.iteration() <= 2 ? "initial@Field_eventually" : "";
                assertEquals(delay, d.fieldAnalysis().getValue().causesOfDelay().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                MethodAnalysis.Eventual eventual = d.methodAnalysis().getEventual();
                if (d.iteration() <= 2) {
                    assertTrue(eventual.causesOfDelay().isDelayed());
                } else {
                    assertTrue(eventual.mark());
                }
            }
            if ("getEventually".equals(d.methodInfo().name)) {
                assertDv(d, 4, MultiLevel.EVENTUALLY_IMMUTABLE_AFTER_MARK_DV, Property.IMMUTABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Eventually".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EVENTUALLY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
        };

        testClass("E2InContext_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    /* small variant on 1; here the field is public */

    @Test
    public void test_3() throws IOException {
        testClass("E2InContext_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
