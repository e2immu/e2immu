
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

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_36_Cast extends CommonTestRunner {


    public Test_36_Cast() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("Cast_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Cast_1".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getTransparentTypes().isEmpty(),
                        () -> "Have " + d.typeAnalysis().getTransparentTypes().toString());
                int expectImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE_NOT_E2IMMUTABLE;
                assertEquals(expectImm, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }

            if ("Counter".equals(d.typeInfo().simpleName)) {
                int expectImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.MUTABLE;
                assertEquals(expectImm, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("incrementedT".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = d.iteration() == 0 ? "<m:increment>" : "instance type int";
                    assertEquals(expectValue, d.currentValue().toString());
                    int expectImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE; // = int
                    assertEquals(expectImm, d.getProperty(VariableProperty.IMMUTABLE));
                }
                if (d.variable() instanceof FieldReference fr && "t".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() == 0 ? "<f:t>" : "instance type T";
                    assertEquals(expectValue, d.currentValue().toString());
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());

                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }
            if ("getTAsString".equals(d.methodInfo().name) && d.variable() instanceof ReturnVariable) {
                assertEquals("t", d.currentValue().toString());
                assertTrue(d.currentValue() instanceof PropertyWrapper pw &&
                        pw.castType().equals(d.evaluationContext().getPrimitives().stringParameterizedType));
            }
            if ("getTAsCounter".equals(d.methodInfo().name) && d.variable() instanceof ReturnVariable) {
                assertEquals("t", d.currentValue().toString());
                assertTrue(d.currentValue() instanceof PropertyWrapper pw &&
                        "Counter".equals(Objects.requireNonNull(pw.castType().typeInfo).simpleName));
                int expectImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.MUTABLE; // = Counter
                assertEquals(expectImm, d.getProperty(VariableProperty.IMMUTABLE));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                int expectMom = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMom, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("incrementedT".equals(d.methodInfo().name)) {
                int expectMom = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMom, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
            if ("Cast_1".equals(d.methodInfo().name)) {
                ParameterAnalysis input = d.parameterAnalyses().get(0);
                int expectMom = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMom, input.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        testClass("Cast_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
