
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

package org.e2immu.analyser.parser.minor;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class Test_23_ExternalContainer extends CommonTestRunner {
    public Test_23_ExternalContainer() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            assertFalse(d.allowBreakDelay());

            if ("print".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "iField".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        // link in_hc_of instead of dependent, even if iField is formally mutable, locally not modified
                        String linked = d.iteration() < 3 ? "in:-1,this:-1" : "in:3,this:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 3, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTEXT_CONTAINER);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 3, MultiLevel.NOT_CONTAINER_DV, Property.CONTEXT_CONTAINER);
                    }
                }
            }
            if ("go".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "myNonContainer".equals(fr.fieldInfo.name)) {
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 3, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                        assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTEXT_CONTAINER);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "myContainerLinkedToParameter".equals(fr.fieldInfo.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertDv(d, 4, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                        assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                        assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTEXT_CONTAINER);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("go".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 3,
                            null != d.haveError(Message.Label.MODIFICATION_NOT_ALLOWED));
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("print".equals(d.methodInfo().name)) {
                assertDv(d.p(0), MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d.p(0), MultiLevel.IGNORE_MODS_DV, Property.IGNORE_MODIFICATIONS);
                assertDv(d.p(0), DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("ExternalContainer_0".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                assertDv(d.p(0), 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                assertEquals(d.iteration() < 2, p0.assignedToFieldDelays().isDelayed());
                if (d.iteration() > 1) {
                    assertEquals("{myContainerLinkedToParameter=assigned:1}", p0.getAssignedToField().toString());
                }
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
            if ("setI".equals(d.methodInfo().name)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("getI".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept".equals(d.methodInfo().name) && "MyNonContainer".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d.p(0), 1, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept".equals(d.methodInfo().name) && "MyContainer".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("myNonContainer".equals(d.fieldInfo().name)) {
                assertDv(d, 2, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
            }
            if ("myContainer".equals(d.fieldInfo().name)) {
                assertDv(d, 4, MultiLevel.CONTAINER_DV, Property.CONTAINER_RESTRICTION);
            }
            if ("myContainerLinkedToParameter".equals(d.fieldInfo().name)) {
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.EXTERNAL_IMMUTABLE);
                assertLinked(d, d.fieldAnalysis().getLinkedVariables(),
                        it0("consumer:-1,this:-1"),
                        it(1, 3, "consumer:-1,this:-1"), it(4, "consumer:0"));
            }
            if ("iField".equals(d.fieldInfo().name)) {
                // value TRUE but annotation will not be visible
                assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, 3, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                assertDv(d, 3, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("I".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            if ("MyContainer".equals(d.typeInfo().simpleName)) {
                assertDv(d, 3, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            if ("MyNonContainer".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("--------", d.delaySequence());

        // modification not allowed (breach of @Container contract on parameter)
        testClass("ExternalContainer_0", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("print".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "iField".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTEXT_CONTAINER);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 3, MultiLevel.NOT_CONTAINER_DV, Property.CONTEXT_CONTAINER);
                    }
                }
            }
            if ("go".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(d.iteration() == 7, d.allowBreakDelay());
                }
                if (d.variable() instanceof FieldReference fr && "myNonContainer".equals(fr.fieldInfo.name)) {
                    if ("2".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTEXT_CONTAINER);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "myContainerLinkedToParameter".equals(fr.fieldInfo.name)) {
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 7, MultiLevel.NOT_CONTAINER_DV, Property.CONTEXT_CONTAINER);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "myContainer".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTEXT_CONTAINER);
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("print".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                assertDv(d.p(0), MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                assertDv(d.p(0), MultiLevel.NOT_IGNORE_MODS_DV, Property.IGNORE_MODIFICATIONS);
                assertDv(d.p(0), 1, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("ExternalContainer_0".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
            if ("setI".equals(d.methodInfo().name)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("getI".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept".equals(d.methodInfo().name) && "MyNonContainer".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d.p(0), 1, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept".equals(d.methodInfo().name) && "MyContainer".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept".equals(d.methodInfo().name) && "Consumer".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), DV.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("myNonContainer".equals(d.fieldInfo().name)) {
                assertDv(d, 2, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
            }
            if ("myContainer".equals(d.fieldInfo().name)) {
                assertDv(d, 4, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
            }
            if ("myContainerLinkedToParameter".equals(d.fieldInfo().name)) {
                assertDv(d, 7, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
            }
            if ("iField".equals(d.fieldInfo().name)) {
                assertDv(d, 3, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("I".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            if ("MyContainer".equals(d.typeInfo().simpleName)) {
                assertDv(d, 3, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            if ("MyNonContainer".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
        };
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-------S--", d.delaySequence());

        testClass("ExternalContainer_1", 0, 0, new DebugConfiguration.Builder()
             //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
             //   .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
            //    .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }


    @Test
    public void test_2() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("print".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);

                assertDv(d.p(0), MultiLevel.IGNORE_MODS_DV, Property.IGNORE_MODIFICATIONS);
                assertDv(d.p(0), DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d, 3, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("ExternalContainer_0".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
            if ("setI".equals(d.methodInfo().name)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("getI".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept".equals(d.methodInfo().name) && "MyNonContainer".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d.p(0), 1, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept".equals(d.methodInfo().name) && "MyContainer".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("myNonContainer".equals(d.fieldInfo().name)) {
                assertDv(d, 2, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
            }
            if ("myContainer".equals(d.fieldInfo().name)) {
                assertDv(d, 4, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
            }
            if ("myContainerLinkedToParameter".equals(d.fieldInfo().name)) {
                assertDv(d, 7, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
            }
            if ("iField".equals(d.fieldInfo().name)) {
                assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, 3, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                assertDv(d, 3, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("I".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            if ("MyContainer".equals(d.typeInfo().simpleName)) {
                assertDv(d, 3, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            if ("MyNonContainer".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
        };
        testClass("ExternalContainer_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }
}
