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

package org.e2immu.analyser.parser.own.util;

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_63_ListUtilSimplified extends CommonTestRunner {

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("joinLists".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "list2".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("nullable instance type List<L>", d.currentValue().toString());
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("nullable instance type List<L>", d.currentValue().toString());
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if ("t1".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type K", d.currentValue().toString());
                    }
                }
                if ("it2".equals(d.variableName())) {
                    String expected = d.iteration() == 0 ? "<vl:it2>" : "list2.iterator()";
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("t2".equals(d.variableName())) {
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<s:L>" : "it2$1.next()";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("joinLists".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d.p(0), MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d.p(1), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d.p(1), MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
            }
        };
        testClass("ListUtilSimplified_0", 1, 3,
                new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeContextPropertiesOverAllMethods(true)
                        .setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("joinLists".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "list2".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("nullable instance type List<L>", d.currentValue().toString());
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("nullable instance type List<L>", d.currentValue().toString());
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if ("t1".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type K", d.currentValue().toString());
                    }
                }
                if ("it2".equals(d.variableName())) {
                    String expected = d.iteration() == 0 ? "<vl:it2>" : "list2.iterator()";
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("t2".equals(d.variableName())) {
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<s:L>" : "list2$1.remove(0)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("joinLists".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d.p(0), MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d.p(1), 1, MultiLevel.NULLABLE_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d.p(1), MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
            }
        };
        testClass("ListUtilSimplified_1", 1, 1,
                new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeContextPropertiesOverAllMethods(true)
                        .setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
