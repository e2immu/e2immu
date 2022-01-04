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

package org.e2immu.analyser.parser.modification;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_16_Modification extends CommonTestRunner {

    public Test_16_Modification() {
        super(true);
    }

    @Test
    public void test7() throws IOException {
        testClass("Modification_7", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test8() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Modification_8".equals(d.methodInfo().name) && d.variable() instanceof FieldReference fr
                    && "set".equals(fr.fieldInfo.name)) {
                assertEquals("input/*@NotNull*/", d.currentValue().toString());
                assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(Property.IMMUTABLE));
            }
        };
        testClass("Modification_8", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test13() throws IOException {
        final String INNER_THIS = "org.e2immu.analyser.testexample.Modification_13.Inner.this";
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("clearIfExceeds".equals(d.methodInfo().name) && INNER_THIS.equals(d.variableName())) {
                assertEquals(DV.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("clearIfExceeds".equals(d.methodInfo().name)) {
                assertEquals(DV.TRUE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }
        };
        testClass("Modification_13", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test14() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Modification_14".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo input && "input".equals(input.name)) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        if (d.iteration() > 0) {
                            VariableInfo eval = d.variableInfoContainer().best(VariableInfoContainer.Level.EVALUATION);
                            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, eval.getProperty(Property.EXTERNAL_NOT_NULL));
                            assertTrue(d.variableInfoContainer().hasMerge());
                            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.EXTERNAL_NOT_NULL));
                        }
                    }
                }
                if (d.variable() instanceof FieldReference fieldReference
                        && "input".equals(fieldReference.fieldInfo.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("input".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        d.fieldAnalysis().getProperty(Property.EXTERNAL_NOT_NULL));
                assertEquals("input", d.fieldAnalysis().getValue().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TwoIntegers".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };

        // ! no warning @NotNull on field -> if(...)
        testClass("Modification_14", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test15() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("TwoIntegers".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };

        /*
        in iteration 2, statementAnalysis should copy the IMMUTABLE value of 1 of input into the variable's properties
         */
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Modification_15".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "input".equals(pi.name)) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertTrue(d.iteration() < 2 || "1".equals(d.statementId()));
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                        assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                        assertDv(d, 0, MultiLevel.MUTABLE_DV, Property.CONTEXT_IMMUTABLE);
                        assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                    }

                } else if (d.variable() instanceof FieldReference fr && "input".equals(fr.fieldInfo.name)) {
                    assertEquals("1", d.statementId());
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                    assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                } else if (d.variable() instanceof This) {
                    assertDv(d, 0, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                    assertDv(d, 0, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_IMMUTABLE);
                } else {
                    fail("?" + d.variableName());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Modification_15".equals(d.methodInfo().name)) {
                assertDv(d.p(0), MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testClass("Modification_15", 1, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test16() throws IOException {
        // one on the type, one error on the method
        testClass("Modification_16_M", 2, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test17() throws IOException {
        // statics
        testClass("Modification_17", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // static method reference
    @Test
    public void test18() throws IOException {
        // statics
        testClass("Modification_18", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test21() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("C1".equals(d.typeInfo().simpleName)) {
                assertEquals("Type java.util.Set<java.lang.String>", d.typeAnalysis().getTransparentTypes().toString());
            }
            if ("Modification_21".equals(d.typeInfo().simpleName)) {
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        testClass("Modification_21", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test22() throws IOException {
        testClass("Modification_22", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
