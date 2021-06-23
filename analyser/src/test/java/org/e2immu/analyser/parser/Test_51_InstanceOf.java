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
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_51_InstanceOf extends CommonTestRunner {

    public Test_51_InstanceOf() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId())) {
                        String expect = "in instanceof Number&&null!=in?\"Number: \"+in:<return value>";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("!(in instanceof Number)||null==in",
                            d.conditionManagerForNextStatement().state().toString());
                }
            }
        };

        testClass("InstanceOf_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("InstanceOf_1".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                if (d.variable() instanceof FieldReference fr && "number".equals(fr.fieldInfo.name)) {
                    assertEquals("in instanceof Number&&null!=in?in:3.14", d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
                if (d.variable() instanceof LocalVariableReference lvr && "number".equals(lvr.simpleName())) {
                    assertEquals("in", d.currentValue().toString());
                }
            }
        };

        testClass("InstanceOf_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof LocalVariableReference lvr && "number".equals(lvr.simpleName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("in", d.currentValue().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("in", d.currentValue().toString());
                    }
                    assertNotEquals("1", d.statementId());
                }
                if (d.variable() instanceof LocalVariableReference lvr && "integer".equals(lvr.simpleName())) {
                    assertEquals("in", d.currentValue().toString());
                    assertNotEquals("0.1.1", d.statementId());
                    assertNotEquals("1", d.statementId());
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        assertEquals("\"Integer: \"+in", d.currentValue().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("in instanceof Integer?\"Integer: \"+in:<return value>", d.currentValue().toString());
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertEquals("in instanceof Integer&&in instanceof Number&&null!=in?\"Integer: \"+in:\"Number: \"+in", d.currentValue().toString());
                    }
                    if("0".equals(d.statementId())) {
                        assertEquals("in instanceof Number&&null!=in?in instanceof Integer?\"Integer: \"+in:\"Number: \"+in:<return value>", d.currentValue().toString());
                    }
                }
            }
        };

        testClass("InstanceOf_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("InstanceOf_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertFalse(d.variable() instanceof LocalVariableReference,
                            "Found " + d.variable().fullyQualifiedName() + " in if() { } part");
                }

                if ("1".equals(d.statementId()) && "number".equals(d.variableName())) {
                    assertEquals("in", d.currentValue().toString());
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.Pattern pattern) {
                        assertFalse(pattern.isPositive());
                        assertEquals("", pattern.parentBlockIndex());
                        assertEquals("1", pattern.scope());
                    }
                }
            }
        };

        testClass("InstanceOf_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    if (d.variableInfoContainer().variableNature() instanceof VariableNature.Pattern pattern) {
                        assertFalse(pattern.isPositive());
                        assertEquals("1", pattern.parentBlockIndex());
                        assertEquals("1.1.0", pattern.scope());
                    }
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertFalse(d.variableInfoContainer().variableNature() instanceof VariableNature.Pattern,
                            "Found " + d.variable().fullyQualifiedName() + " in if() { } part");
                }
                if ("1.1.0".equals(d.statementId()) && "number".equals(d.variableName())) {
                    assertEquals("in", d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    assertFalse(d.variableInfoContainer().variableNature() instanceof VariableNature.Pattern,
                            "Found " + d.variable().fullyQualifiedName() + " in 2");
                }
            }
        };

        testClass("InstanceOf_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_6() throws IOException {
        testClass("InstanceOf_6", 3, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_7() throws IOException {
        testClass("InstanceOf_7", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_8() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("in instanceof String&&null!=in", d.evaluationResult().getExpression().toString());
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("null!=in", d.evaluationResult().getExpression().toString());
                }
            }
        };

        testClass("InstanceOf_8", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }
}
