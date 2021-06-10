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
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_61_OutputBuilderSimplified extends CommonTestRunner {

    public Test_61_OutputBuilderSimplified() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("OutputBuilderSimplified_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_1() throws IOException {
        testClass("OutputBuilderSimplified_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        final int HIGH = 50;
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("go".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        int expectExtImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                        assertEquals(expectExtImm, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));

                        int expectExtNN = d.iteration() <= 1 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                        assertEquals(expectExtNN, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    }
                }
                if (d.variable() instanceof ParameterInfo p && "o1".equals(p.name)) {
                    assertEquals(Level.DELAY, d.getProperty(VariableProperty.CONTEXT_IMMUTABLE_DELAY));

                    int cImm = d.getProperty(VariableProperty.CONTEXT_IMMUTABLE);
                    if ("0".equals(d.statementId())) {
                        String expectedValue = d.iteration() <= 1 ? "<p:o1>" : "nullable instance type OutputBuilderSimplified_2";
                        assertEquals(expectedValue, d.currentValue().toString());

                        String expectedLinked = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                        assertEquals(expectedLinked, d.variableInfo().getLinkedVariables().toString());

                        int expectContextMod = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectContextMod, d.getProperty(VariableProperty.CONTEXT_MODIFIED));

                        // links have not been established
                        int expectContextImm = d.iteration() <= 2 ? Level.DELAY : MultiLevel.MUTABLE;
                        assertEquals(expectContextImm, cImm);
                    }
                }
                if (d.variable() instanceof ParameterInfo p && "o2".equals(p.name)) {
                    assertEquals(Level.DELAY, d.getProperty(VariableProperty.CONTEXT_IMMUTABLE_DELAY));

                    int cImm = d.getProperty(VariableProperty.CONTEXT_IMMUTABLE);
                    if ("0".equals(d.statementId())) {
                        assertEquals(MultiLevel.MUTABLE, cImm);
                    }
                    if ("1".equals(d.statementId())) {
                        String expectedValue = d.iteration() <= 1 ? "<p:o2>" : "nullable instance type OutputBuilderSimplified_2";
                        assertEquals(expectedValue, d.currentValue().toString());

                        String expectedLinked = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                        assertEquals(expectedLinked, d.variableInfo().getLinkedVariables().toString());

                        int expectContextMod = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectContextMod, d.getProperty(VariableProperty.CONTEXT_MODIFIED));

                        // links have not been established
                        int expectContextImm = d.iteration() <= 2 ? Level.DELAY : MultiLevel.MUTABLE;
                        assertEquals(expectContextImm, cImm);
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        int expectImm = d.iteration() <= 2 ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE_NOT_E2IMMUTABLE;
                        assertEquals(expectImm, d.getProperty(VariableProperty.IMMUTABLE));
                    }
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("OutputBuilderSimplified_2".equals(d.typeInfo().simpleName)) {
                int expectImmutable = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE_NOT_E2IMMUTABLE;
                assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        // SUMMARY: in iteration 4, o2 should have IMMUTABLE = @E1Immutable
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("go".equals(d.methodInfo().name)) {
                for (ParameterAnalysis param : d.parameterAnalyses()) {
                    // no direct link with a parameter which has to be/will be dynamically immutable
                    assertEquals(MultiLevel.NOT_INVOLVED, param.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));

                    int expectContextImm = d.iteration() <= 3 ? Level.DELAY : MultiLevel.MUTABLE;
                    assertEquals(expectContextImm, param.getProperty(VariableProperty.CONTEXT_IMMUTABLE));

                    int expectImm = d.iteration() <= 3 ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE_NOT_E2IMMUTABLE;
                    assertEquals(expectImm, param.getProperty(VariableProperty.IMMUTABLE));
                }
            }
            if ("isEmpty".equals(d.methodInfo().name)) {
                int expectMm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };

        testClass("OutputBuilderSimplified_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("OutputBuilderSimplified_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
