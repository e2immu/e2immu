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

package org.e2immu.analyser.parser.failing;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_57_Lambda extends CommonTestRunner {

    public Test_57_Lambda() {
        super(false);
    }

    // System.out potential null pointer
    @Test
    public void test_0() throws IOException {
        testClass("Lambda_0", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("Lambda_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_2() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get".equals(d.methodInfo().name) && d.iteration() > 0) {
                assertEquals("i$0", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("method".equals(d.methodInfo().name) && d.iteration() > 0) {
                Expression srv = d.methodAnalysis().getSingleReturnValue();
                assertEquals("j*i$1", srv.toString());
            }
        };
        testClass("Lambda_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("j".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<m:get>" : "x.k";
                        assertEquals(expect, d.currentValue().toString());
                        assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                    }
                }
                if ("f".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:get>" : "x.k";
                        assertEquals(expected, d.currentValue().toString());
                        if (d.iteration() > 0) {
                            if (d.currentValue() instanceof InlinedMethod inlinedMethod) {
                                assertEquals(2, inlinedMethod.variables().size()); // x, x.k
                                assertFalse(inlinedMethod.containsVariableFields());
                            } else fail("Class " + d.currentValue().getClass());
                        }
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && d.iteration() > 0) {
                assertEquals("x.k>=3?x.k*i$1:3", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("k".equals(d.fieldInfo().name)) {
                assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
            }
        };

        testClass("Lambda_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("j".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<m:get>" : "x.k";
                        assertEquals(expect, d.currentValue().toString());
                        assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && d.iteration() > 0) {
                assertEquals("x.k>=3?x.k*i$1:3", d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass("Lambda_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_5() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                Expression e = d.methodAnalysis().getSingleReturnValue();
                assertEquals("a*a+b*-b+a*b+a*-b", e.toString());
                assertTrue(e instanceof InlinedMethod);
            }
            if ("direct".equals(d.methodInfo().name)) {
                Expression e = d.methodAnalysis().getSingleReturnValue();
                assertEquals("a*a+b*-b+a*b+a*-b", e.toString());
                assertTrue(e instanceof InlinedMethod);
            }
        };
        testClass("Lambda_5", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_6() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                Expression e = d.methodAnalysis().getSingleReturnValue();
                if (d.iteration() > 0) {
                    assertEquals("a*a+a*b+a*-i+b*-i", e.toString());
                    assertTrue(e instanceof InlinedMethod);
                }
            }
            if ("direct".equals(d.methodInfo().name)) {
                Expression e = d.methodAnalysis().getSingleReturnValue();
                if (d.iteration() > 0) {
                    assertEquals("a*a+a*b+a*-i+b*-i", e.toString());
                    assertTrue(e instanceof InlinedMethod);
                }
            }
        };
        testClass("Lambda_6", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_7() throws IOException {
        testClass("Lambda_7", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}