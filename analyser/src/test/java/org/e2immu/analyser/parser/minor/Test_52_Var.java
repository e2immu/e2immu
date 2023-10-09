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
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
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
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("s".equals(d.variableName())) {
                    assertTrue(d.statementId().startsWith("2"), "Present in " + d.statementId());
                }
            }
        };
        // xes potential null pointer warning, x potential null pointer warning
        testClass("Var_2", 0, 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    // for (var x : xes) { ... } indirect child
    @Test
    public void test_3() throws IOException {
        // x potential null pointer warning
        testClass("Var_3", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3_0() throws IOException {
        testClass("Var_3", 0, 0, new DebugConfiguration.Builder()
                        .build(),
                new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }

    // lambda
    @Test
    public void test_4() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<m:apply>" : "Var_4.repeater(3).apply(\"y\")";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.iteration() > 0) {
                    assertEquals("/*inline apply*/x.repeat(i)", d.methodAnalysis().getSingleReturnValue().toString());
                }
                // @NotNull on parameter of apply
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }
            if ("repeater".equals(d.methodInfo().name)) {
                String expect = d.iteration() == 0 ? "<m:repeater>" : "instance type $1";
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---", d.delaySequence());
        testClass("Var_4", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // try
    @Test
    public void test_5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("sw".equals(d.variableName())) {
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("instance type StringWriter", d.currentValue().toString());
                    assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.TryResource);
                } else if ("0".equals(d.statementId())) {
                    assertEquals("new StringWriter()", d.variableInfoContainer()
                            .best(Stage.EVALUATION).getValue().toString());
                    assertFalse(d.variableInfoContainer().hasMerge());
                } else {
                    fail(d.statementId()); // sw should not exist here! (0.1.0, catch clause)
                }
            }
            if (d.variable() instanceof ReturnVariable) {
                if ("0.0.0".equals(d.statementId())) {
                    assertEquals("(instance type StringWriter/*{L sw:0}*/).toString()", d.currentValue().toString());
                    // explicit as result of the method, rather than governed by the type
                    assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, d.getProperty(Property.IMMUTABLE));
                }
                if ("0.1.0".equals(d.statementId())) {
                    assertEquals("\"Error!\"", d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, d.getProperty(Property.IMMUTABLE));
                }
            }
        };
        TypeMapVisitor typeMapVisitor = d -> {
            TypeInfo typeInfo = d.typeMap().get(String.class);
            DV imm = d.getTypeAnalysis(typeInfo).getProperty(Property.IMMUTABLE);
            assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, imm);
        };

        testClass("Var_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }
}
