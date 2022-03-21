
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
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_25_FieldReference extends CommonTestRunner {

    public Test_25_FieldReference() {
        super(false);
    }

    @Test
    public void test0() throws IOException {
        testClass("FieldReference_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // crucial here: seen in "0.0" vs seen in "0", i.e., cd is a local variable; it should never get to the
    // merge block of "0"; neither should its field access cd.properties.
    // the method ensuring this is "SAEvaluationContext.replaceLocalVariables"
    @Test
    public void test1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("cd".equals(d.variableName())) {
                    assertTrue(d.statementId().startsWith("0.0"), "Seen in " + d.statementId());
                }
                if (d.variable() instanceof FieldReference fr && "changeData".equals(fr.fieldInfo.name)) {
                    assertCurrentValue(d, 1, "nullable instance type Map<String,ChangeData>");
                    assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTEXT_CONTAINER);
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("changeData".equals(d.fieldInfo().name)) {
                assertEquals("Map.of(\"X\",new ChangeData(Map.of(\"3\",3)))", d.fieldAnalysis().getValue().toString());

                // MUTABLE because without A API
                assertDv(d, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.EXTERNAL_CONTAINER);
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get".equals(d.methodInfo().name) && "ChangeData".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, DV.FALSE_DV, Property.IDENTITY);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                String expected = d.iteration() == 0 ? "<m:get>" : "properties.get(s)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("properties".equals(d.methodInfo().name) && "ChangeData".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d, DV.FALSE_DV, Property.IDENTITY);
                String expected = d.iteration() == 0 ? "<m:properties>" : "properties";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() > 0) assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ChangeData".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };
        // potential null pointer exceptions
        testClass("FieldReference_1", 0, 3, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    Set<String> scopes = Set.of("xx", "xx$0", "new X(xx.i)");
                    assertTrue(scopes.contains(fr.scope.toString()));
                }
            }
        };
        // potential null pointer
        // unused parameter j
        testClass("FieldReference_2", 0, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("getProperty".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "setP".equals(fr.fieldInfo.name)) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
            if ("copy".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "setP".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("2".equals(d.statementId())) {
                        String delayed = d.iteration() == 0
                                ? "cm:this.setP@Method_copy_2-E;initial:this.setP@Method_copy_0-C"
                                : "mom@Parameter_setP";
                        assertDv(d, delayed, 2, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("setP".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "setP".equals(fr.fieldInfo.name)) {
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
            if ("method".equals(d.methodInfo().name)) {
                if ("notNull".equals(d.variableName())) {
                    if ("2.0.0.0.0".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                        // assigned the null constant, yet we have no idea about its value properties
                        String aNull = switch (d.iteration()) {
                            case 0 -> "<vp::container@Record_DV>";
                            case 1 -> "<vp::initial@Field_v>";
                            default -> "null";
                        };
                        assertEquals(aNull, d.currentValue().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("ParameterAnalysis".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                String delayed = d.iteration() <= 1 ? "mom@Parameter_setP" : "break_mom_delay@Parameter_setP;mom@Parameter_setP";
                assertDv(d.p(0), delayed, 3, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("setP".equals(d.fieldInfo().name)) {
                assertDv(d, 2, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("DV".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };
        testClass("FieldReference_3", 0, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }
}
