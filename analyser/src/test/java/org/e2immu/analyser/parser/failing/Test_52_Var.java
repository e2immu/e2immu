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
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_52_Var extends CommonTestRunner {

    public Test_52_Var() {
        super(true);
    }

    // basics
    @Test
    public void test_0() throws IOException {
        testClass("Var_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // var set = new HashSet<>(xes);
    @Test
    public void test_1() throws IOException {
        testClass("Var_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // for (var x : xes) { ... } direct child
    @Test
    public void test_2() throws IOException {
        testClass("Var_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // for (var x : xes) { ... } indirect child
    @Test
    public void test_3() throws IOException {
        testClass("Var_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // lambda
    @Test
    public void test_4() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("\"y\".repeat(3)", d.evaluationResult().value().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                    assertEquals("apply", inlinedMethod.methodInfo().name);
                    assertEquals("x.repeat(i)", d.methodAnalysis().getSingleReturnValue().toString());
                } else fail();

                // @NotNull on parameter of apply
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }
            if ("repeater".equals(d.methodInfo().name)) {
                if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                    assertEquals("repeater", inlinedMethod.methodInfo().name);
                    if (inlinedMethod.expression() instanceof InlinedMethod inlinedMethod2) {
                        assertEquals("apply", inlinedMethod2.methodInfo().name);
                        assertEquals("x.repeat(i)", d.methodAnalysis().getSingleReturnValue().toString());
                        assertFalse(inlinedMethod.containsVariableFields());
                        assertEquals(2, inlinedMethod.variables(true).size());
                        assertTrue(inlinedMethod.variables(true).stream().allMatch(v -> v instanceof ParameterInfo));
                    } else fail();
                } else fail();
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }
        };
        // no annotated API, so we have no idea if repeater is @NotNull or not -> repeater(3) may return null
        testClass("Var_4", 0, 1, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    // try
    @Test
    public void test_5() throws IOException {
        // expect one potential null ptr (result of sw.append())
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("sw".equals(d.variableName())) {
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("new StringWriter()", d.currentValue().toString());
                    assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.TryResource);
                } else if ("0".equals(d.statementId())) {
                    assertEquals("new StringWriter()", d.variableInfoContainer()
                            .best(VariableInfoContainer.Level.EVALUATION).getValue().toString());
                    assertTrue(d.variableInfoContainer().hasMerge());
                    assertEquals("new StringWriter()", d.currentValue().toString());
                } else {
                    fail(d.statementId()); // sw should not exist here! (0.1.0, catch clause)
                }
            }
            if (d.variable() instanceof ReturnVariable) {
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("sw.toString()", d.currentValue().toString());
                    // explicit as result of the method, rather than governed by the type
                    assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, d.getProperty(Property.IMMUTABLE));
                    assertEquals("sw.toString()", d.currentValue().toString());
                }
                if ("0.1.0".equals(d.statementId())) {
                    assertEquals("\"Error!\"", d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, d.getProperty(Property.IMMUTABLE));
                }
            }
        };
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo typeInfo = typeMap.get(String.class);
            DV imm = typeInfo.typeAnalysis.get().getProperty(Property.IMMUTABLE);
            assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, imm);
        };

        testClass("Var_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }
}
