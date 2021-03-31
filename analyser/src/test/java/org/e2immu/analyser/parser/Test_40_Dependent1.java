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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_40_Dependent1 extends CommonTestRunner {
    public Test_40_Dependent1() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("Dependent1_0", 0, 0,
                new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo t && "t".equals(t.name)) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertEquals(MultiLevel.DEPENDENT, d.getProperty(VariableProperty.CONTEXT_DEPENDENT));
                    }
                    if ("2".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        int expectDependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.DEPENDENT_1;
                        assertEquals(expectDependent, d.getProperty(VariableProperty.CONTEXT_DEPENDENT));
                    }
                }
            }
        };
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo list = typeMap.get(List.class);
            MethodInfo add = list.findUniqueMethod("add", 1);
            int methodIndependent = add.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT);
            assertEquals(MultiLevel.DEPENDENT, methodIndependent);
            int paramIndependent = add.methodInspection.get().getParameters().get(0).parameterAnalysis.get()
                    .getProperty(VariableProperty.INDEPENDENT_PARAMETER);
            assertEquals(MultiLevel.DEPENDENT_1, paramIndependent);
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectIndependent = d.iteration() <= 1 ? Level.DELAY : MultiLevel.DEPENDENT_1;
                assertEquals(expectIndependent, p0.getProperty(VariableProperty.CONTEXT_DEPENDENT));
            }
            if ("add2".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectIndependent = d.iteration() <= 1 ? Level.DELAY : MultiLevel.DEPENDENT_1;
                assertEquals(expectIndependent, p0.getProperty(VariableProperty.CONTEXT_DEPENDENT));
                assertEquals(expectIndependent, p0.getProperty(VariableProperty.INDEPENDENT_PARAMETER));
            }
        };

        testClass("Dependent1_1", 0, 0,
                new DebugConfiguration.Builder()
                        .addTypeMapVisitor(typeMapVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build());
    }

    @Test
    public void test_2() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectIndependent = d.iteration() <= 1 ? Level.DELAY : MultiLevel.DEPENDENT_1;
                assertEquals(expectIndependent, p0.getProperty(VariableProperty.INDEPENDENT_PARAMETER));
            }
            if ("addWithMessage".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectIndependent = d.iteration() <= 2 ? Level.DELAY : MultiLevel.DEPENDENT_1;
                assertEquals(expectIndependent, p0.getProperty(VariableProperty.CONTEXT_DEPENDENT));
                assertEquals(expectIndependent, p0.getProperty(VariableProperty.INDEPENDENT_PARAMETER));
            }
        };

        testClass("Dependent1_2", 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build());
    }
}
