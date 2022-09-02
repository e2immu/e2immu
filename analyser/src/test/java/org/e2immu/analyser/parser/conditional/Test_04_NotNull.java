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
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_04_NotNull extends CommonTestRunner {
    public Test_04_NotNull() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        testClass("NotNull_0", 0, 1, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(false).build());
    }

    @Test
    public void test_0_1() throws IOException {
        testClass("NotNull_0", 2, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }


    @Test
    public void test_1() throws IOException {
        testClass("NotNull_1", 0, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(false).build());
    }

    @Test
    public void test_1_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("lowerCase".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "s".equals(fr.fieldInfo.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        String linked = d.iteration() <= 3 ? "this.s:-1" : "";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("lowerCase".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 3, d.context().evaluationContext().allowBreakDelay());
                }
                if ("0.0.0".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1, 2 -> "<null-check>";
                        case 3 -> "null!=<vp:s:ext_not_null@Field_s;initial:this.s@Method_lowerCase_0-C>";
                        default -> "null!=s";
                    };
                    assertEquals(expected, d.condition().toString());
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s".equals(d.fieldInfo().name)) {
                assertDv(d, 3, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("lowerCase".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 3 ? "<m:lowerCase>"
                        : "/*inline lowerCase*/null==s?\"?\":s.toLowerCase()";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("NotNull_1".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 4, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        testClass("NotNull_1", 0, 0, new DebugConfiguration.Builder()
                   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("NotNull_2", 0, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(false).build());
    }

    @Test
    public void test_2_1() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("lowerCase".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s".equals(d.fieldInfo().name)) {
                assertDv(d, 3, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
            }
        };
        testClass("NotNull_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }

    // unique combination fails: no AAPI, import with a * -> List inspection not known.
    @Test
    public void test_4_1() throws IOException {
        testClass("NotNull_4_1", 3, 1, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }


    @Test
    public void test_5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("reInitialize".equals(d.methodInfo().name)) {
                if (d.variable() instanceof DependentVariable dv
                        && dv.arrayVariable() instanceof FieldReference fr
                        && "strings".equals(fr.fieldInfo.name)) {
                    if ("1.0.0.0.1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<s:String>" : "s";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("s".equals(d.variableName())) {
                    if ("1.0.0.0.1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<v:s>" : "nullable instance type String";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("iv-44:25".equals(d.variableName())) {
                    // the index variable acting for the i++ expression
                    if ("1.0.0.0.1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<v:i>" : "i$1.0.0";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("NotNull_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().build());
    }
}
