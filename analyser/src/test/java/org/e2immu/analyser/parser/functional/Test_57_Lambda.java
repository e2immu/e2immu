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

package org.e2immu.analyser.parser.functional;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.ConstructorCall;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Collector;

import static org.junit.jupiter.api.Assertions.*;

public class Test_57_Lambda extends CommonTestRunner {

    public Test_57_Lambda() {
        super(false);
    }

    // System.out potential null pointer
    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("collector".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("collector".equals(d.methodInfo().name)) {
                if (d.iteration() >= 2) {
                    Expression srv = d.methodAnalysis().getSingleReturnValue();
                    if (srv instanceof InlinedMethod inline) {
                        if (inline.expression() instanceof ConstructorCall cc) {
                            assertEquals("$1", cc.anonymousClass().simpleName);
                        } else fail("Got " + inline.expression().getClass());
                    } else fail("Got " + srv.getClass());
                }
                assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("$1".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo collector = typeMap.get(Collector.class);
            assertEquals(MultiLevel.NOT_CONTAINER_DV, collector.typeAnalysis.get().getProperty(Property.CONTAINER));
        };
        testClass("Lambda_0", 0, 1, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("Lambda_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("j".equals(d.variableName())) {
                    String expect = d.iteration() == 0 ? "<m:get>" : "i";
                    assertEquals(expect, d.currentValue().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get".equals(d.methodInfo().name) && d.iteration() > 0) {
                assertEquals("i$0", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("method".equals(d.methodInfo().name) && d.iteration() > 0) {
                Expression srv = d.methodAnalysis().getSingleReturnValue();
                assertEquals("i*i$1", srv.toString());
            }
        };
        testClass("Lambda_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("j".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<s:int>" : "x.k";
                        assertEquals(expect, d.currentValue().toString());
                        String expectLv = d.iteration() == 0 ? "f:-1,j:0" : "j:0,x.k:1";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("f".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<s:$1>" : "x.k";
                        assertEquals(expected, d.currentValue().toString());
                        if (d.iteration() > 0) {
                            if (d.currentValue() instanceof InlinedMethod inlinedMethod) {
                                assertEquals(2, inlinedMethod.variables(true).size()); // x, x.k
                                assertFalse(inlinedMethod.containsVariableFields());
                            } else fail("Class " + d.currentValue().getClass());
                        }
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && d.iteration() > 0) {
                assertEquals("x.k<=2?3:x.k*i$1", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("get".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
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
                        String expect = d.iteration() == 0 ? "<s:int>" : "x.k";
                        assertEquals(expect, d.currentValue().toString());
                        String expectLv = d.iteration() == 0 ? "f:-1,j:0" : "j:0,x.k:1";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("get".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "k".equals(fr.fieldInfo.name) && fr.scope instanceof ConstructorCall) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<f:k>" : "instance type int";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("l".equals(d.variableName())) {
                    String expected = d.iteration() == 0 ? "<f:new X(x.k).k>" : "x.k";
                    assertEquals(expected, d.currentValue().toString());
                    assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    if ("0".equals(d.statementId())) {
                        String expectedLv = d.iteration() == 0
                                ? "l:0,new org.e2immu.analyser.parser.functional.testexample.Lambda_4.X(x.k).k:0"
                                : "l:0,new org.e2immu.analyser.parser.functional.testexample.Lambda_4.X(x.k).k:0,x.k:1";
                        assertEquals(expectedLv, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expectedLv = d.iteration() == 0
                                ? "l:0,new org.e2immu.analyser.parser.functional.testexample.Lambda_4.X(x.k).k:-1,return get:0"
                                : "l:0,new org.e2immu.analyser.parser.functional.testexample.Lambda_4.X(x.k).k:0,return get:0,x.k:1";
                        assertEquals(expectedLv, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<f:new X(x.k).k>" : "x.k";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        String lv = d.iteration() == 0
                                ? "l:0,new org.e2immu.analyser.parser.functional.testexample.Lambda_4.X(x.k).k:0,return get:0"
                                : "l:0,new org.e2immu.analyser.parser.functional.testexample.Lambda_4.X(x.k).k:0,return get:0,x.k:1";
                        assertEquals(lv, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("get".equals(d.methodInfo().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);
                if ("0".equals(d.statementId())) {
                    assertTrue(d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(d.iteration() > 0, d.statementAnalysis().methodLevelData().linksHaveBeenEstablished());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && d.iteration() > 0) {
                assertEquals("x.k<=2?3:x.k*i$1", d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass("Lambda_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_5() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                Expression e = d.methodAnalysis().getSingleReturnValue();
                String expected = d.iteration() == 0 ? "<m:method>" : "a*a+b*-b+a*b+a*-b";
                assertEquals(expected, e.toString());
                if (d.iteration() > 0) assertTrue(e instanceof InlinedMethod);
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

    @Test
    public void test_8() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("j".equals(d.variableName())) {
                    String expect = d.iteration() == 0 ? "<m:get>" : "inner.i";
                    assertEquals(expect, d.currentValue().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get".equals(d.methodInfo().name) && d.iteration() > 0) {
                assertEquals("i$0", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("method".equals(d.methodInfo().name) && d.iteration() > 0) {
                Expression srv = d.methodAnalysis().getSingleReturnValue();
                assertEquals("inner.i*inner.i$1", srv.toString());
            }
        };

        testClass("Lambda_8", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    // no A API -> 2 potential null pointer warnings (IMPROVE should be 4?)
    @Test
    public void test_9() throws IOException {
        testClass("Lambda_9", 0, 2, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_10() throws IOException {
        // ignoring result of put(...) because not A API, method is non-modifying
        testClass("Lambda_10", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_11() throws IOException {
        // potential null pointer, System.out
        testClass("Lambda_11", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_12() throws IOException {
        // doesn't get any simpler: there should not be an unused local variable warning!
        testClass("Lambda_12", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_13() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("assigning".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<f:i>" : "instance type int";
                        assertEquals(expected, d.currentValue().toString());

                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("ii".equals(d.fieldInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
            if ("i".equals(d.fieldInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.FINAL);
            }
        };
        // assigning to field outside type = not allowed
        // property value worse than overridden method: Predicate is @NotModified without A API
        testClass("Lambda_13", 2, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    @Test
    public void test_13_2() throws IOException {
        // additional error: @Variable is not seen (we don't look outside the static type)
        testClass("Lambda_13", 3, 0, new DebugConfiguration.Builder()
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(false).build());
    }
}
