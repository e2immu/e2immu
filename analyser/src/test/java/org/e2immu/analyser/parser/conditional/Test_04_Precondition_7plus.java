
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
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_04_Precondition_7plus extends CommonTestRunner {

    public Test_04_Precondition_7plus() {
        super(true);
    }

    @Test
    public void test_7() throws IOException {
        // IMPROVE 8 errors?
        testClass("Precondition_7", 8, 11,
                new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_7_1() throws IOException {
        testClass("Precondition_7_1", 2, 7,
                new DebugConfiguration.Builder().build());
    }

    @Test
    public void test_7_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("normalType".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "findType".equals(pi.name)) {
                    assertEquals("nullable instance type FindType/*@Identity*/", d.currentValue().toString());
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 0, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("4".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "arrays".equals(pi.name)) {
                    if ("3".equals(d.statementId())) {
                        assertEquals("instance type int", d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "wildCard".equals(pi.name)) {
                    if ("3".equals(d.statementId())) {
                        assertEquals("nullable instance type WildCard", d.currentValue().toString());
                    }
                }
                if ("typeInfo".equals(d.variableName())) {
                    if ("3".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<vp:TypeInfo:container@Record_TypeInfo;immutable@Record_TypeInfo>";
                            case 1 -> "<vp:TypeInfo:cm@Parameter_fqn;container@Record_TypeInfo;initial@Field_fqn;mom@Parameter_fqn>";
                            default -> "findType.find(path.toString()/*@NotNull 0==this.length()*/.replaceAll(\"[/$]\",\".\"),path.toString()/*@NotNull 0==this.length()*/)";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("parameterizedType".equals(d.variableName())) {
                    if ("4".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<new:ParameterizedType>"
                                : "new ParameterizedType(typeInfo,arrays,wildCard,typeParameters)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("ParameterizedType".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 0, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(2), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(3), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("find".equals(d.methodInfo().name)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 2, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ParameterizedType".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("TypeInfo".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                assertDv(d, 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };
        testClass("Precondition_7_2", 1, 3,
                new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build());
    }

    @Test
    public void test_8() throws IOException {
        testClass("Precondition_8", 0, 1,
                new DebugConfiguration.Builder()
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setForceAlphabeticAnalysisInPrimaryType(true)
                        .build());
    }

    @Test
    public void test_9() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("pop".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "stack".equals(fr.fieldInfo.name)) {
                    assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("pop".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0
                        ? "Precondition[expression=!<m:isEmpty>, causes=[escape]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(expected, d.methodAnalysis().getPrecondition().toString());
                if (d.iteration() > 0) assertTrue(d.methodAnalysis().getPrecondition().isEmpty());
            }
        };
        testClass("Precondition_9", 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build());
    }

}
