
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

package org.e2immu.analyser.parser.basics;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_00_Basics_15plus extends CommonTestRunner {
    public Test_00_Basics_15plus() {
        super(true);
    }

    @Test
    public void test_15() throws IOException {
        testClass("Basics_15", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // getSimpleName().toLowerCase() causes null-pointer warning
    // test checks that lc is visited, because it has code!!
    @Test
    public void test_16() throws IOException {
        AtomicBoolean visited = new AtomicBoolean();
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("lc".equals(d.methodInfo().name)) {
                assertSame(Analysis.AnalysisMode.COMPUTED, d.methodAnalysis().analysisMode());
                visited.set(true);
            }
        };
        testClass("Basics_16", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
        assertTrue(visited.get());
    }

    @Test
    public void test_17() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            // the assignment this.string = string causes a delay of one iteration (as it is a field)
            if ("Basics_17".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "string".equals(fr.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    }
                }
                if (d.variable() instanceof ParameterInfo p && "string".equals(p.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, CONTEXT_NOT_NULL);
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("string".equals(d.fieldInfo().name)) {
                assertEquals("string", d.fieldAnalysis().getValue().toString());
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, EXTERNAL_IMMUTABLE);
                assertDv(d, 1, MultiLevel.NULLABLE_DV, EXTERNAL_NOT_NULL);
            }
        };

        testClass("Basics_17", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }


    @Test
    public void test_18() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("get".equals(d.methodInfo().name)) {
                if ("4".equals(d.statementId())) {
                    if ("a$1.i".equals(d.variable().toString())) {
                        assertEquals("instance type int", d.currentValue().toString());
                    }
                    if ("a$0.i".equals(d.variable().toString())) {
                        assertEquals("instance type int", d.currentValue().toString());
                    }
                }
            }
            if ("setA".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "a".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(DV.FALSE_DV, d.getProperty(CONTAINER));
                    }
                }
                if (d.variable() instanceof FieldReference fr && "a".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() <= 1
                            ? "<vp:org.e2immu.analyser.testexample.Basics_18.A:immutable@Record_A>" : "a";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                }
            }
        };
        testClass("Basics_18", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_19() throws IOException {
        testClass("Basics_19", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
