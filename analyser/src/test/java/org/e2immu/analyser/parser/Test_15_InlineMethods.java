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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.Sum;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class Test_15_InlineMethods extends CommonTestRunner {
    public Test_15_InlineMethods() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            MethodInfo unaryMinusInt = typeMap.getPrimitives().unaryMinusOperatorInt;
            assertEquals("int.-(int)", unaryMinusInt.fullyQualifiedName());
        };
        testClass("InlineMethods_0", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    /*
    tests that the inlined method leaves no parameter lingering (assert statement in StatementAnalysis.initialValueForReading)
     */
    @Test
    public void test_1() throws IOException {
        testClass("InlineMethods_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("plus".equals(d.methodInfo().name) && d.iteration() > 0) {
                if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                    assertEquals("i+r", inlinedMethod.toString());
                    //  assertSame(InlinedMethod.Applicability.EVERYWHERE, inlinedMethod.applicability());
                } else fail();
            }
            if ("difference31".equals(d.methodInfo().name) && d.iteration() > 0) {
                assertEquals("2", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("difference11".equals(d.methodInfo().name) && d.iteration() > 0) {
                assertEquals("0", d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass("InlineMethods_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("plusRandom".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("i+r", d.evaluationResult().value().toString());
                    assertTrue(d.evaluationResult().value() instanceof Sum);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("plusRandom".equals(d.methodInfo().name)) {
                if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                    assertEquals("i+r", inlinedMethod.toString());
                } else {
                    fail("Have " + d.methodAnalysis().getSingleReturnValue().getClass());
                }
            }
            if ("difference31".equals(d.methodInfo().name)) {
                assertEquals("2+instance type int-(instance type int)", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("difference11".equals(d.methodInfo().name)) {
                assertEquals("instance type int-(instance type int)", d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo random = typeMap.get(Random.class);
            MethodInfo nextInt = random.findUniqueMethod("nextInt", 0);
            MethodAnalysis nextIntAnalysis = nextInt.methodAnalysis.get();
            assertEquals(Level.TRUE, nextIntAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        };

        testClass("InlineMethods_3", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {
        testClass("InlineMethods_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("expand3".equals(d.methodInfo().name)) {
                if ("il5".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("new InlineMethods_5(a)", d.currentValue().toString());
                        if (d.currentValue() instanceof NewObject newObject) {
                            assertEquals(1, newObject.parameterExpressions().size());
                        } else fail();
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("sum".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) assertNull(d.methodAnalysis().getSingleReturnValue());
                else {
                    assertEquals("i+j", d.methodAnalysis().getSingleReturnValue().toString());
                    assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
                }
            }
            if ("sum5".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) assertNull(d.methodAnalysis().getSingleReturnValue());
                else {
                    assertEquals("5+i", d.methodAnalysis().getSingleReturnValue().toString());
                    assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
                }
            }
            if ("expand3".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) assertNull(d.methodAnalysis().getSingleReturnValue());
                else {
                    assertEquals("a+b", d.methodAnalysis().getSingleReturnValue().toString());
                    assertTrue(d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod);
                }
            }
        };
        testClass("InlineMethods_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_6() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("sum".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) assertNull(d.methodAnalysis().getSingleReturnValue());
                else {
                    assertEquals("i$0+j", d.methodAnalysis().getSingleReturnValue().toString());
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertTrue(inlinedMethod.containsVariableFields());
                    } else fail();
                }
            }
            if ("expandSum".equals(d.methodInfo().name) && d.iteration() > 0) {
                //    assertEquals("a+b", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("expand".equals(d.methodInfo().name) && d.iteration() > 0) {
                //    assertEquals("a+b", d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass("InlineMethods_6", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_7() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("expand".equals(d.methodInfo().name) && d.iteration() > 0) {
                assertEquals("a+b", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("doNotExpand".equals(d.methodInfo().name) && d.iteration() > 0) {
                assertEquals("a+b", d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testClass(List.of("InlineMethods6", "InlineMethods_7"), 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().build(), new AnnotatedAPIConfiguration.Builder().build());
    }

}
