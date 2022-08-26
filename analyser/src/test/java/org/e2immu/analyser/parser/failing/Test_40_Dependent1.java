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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.CommonTestRunner;
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
                    }
                    if ("2".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        // TODO
                    }
                }
            }
        };
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo list = typeMap.get(List.class);
            MethodInfo add = list.findUniqueMethod("add", 1);
            DV methodIndependent = add.methodAnalysis.get().getProperty(Property.INDEPENDENT);
            assertEquals(MultiLevel.DEPENDENT_DV, methodIndependent);
            DV paramIndependent = add.methodInspection.get().getParameters().get(0).parameterAnalysis.get()
                    .getProperty(Property.INDEPENDENT);
            assertEquals(MultiLevel.INDEPENDENT_HC_DV, paramIndependent);
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add2".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
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
                assertDv(d.p(0), 2, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
            if ("addWithMessage".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 3, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
        };

        testClass("Dependent1_2", 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("Dependent1_3", 0, 0,
                new DebugConfiguration.Builder()
                        .build());
    }
    @Test
    public void test_4() throws IOException {
        testClass("Dependent1_4", 0, 0,
                new DebugConfiguration.Builder()
                        .build());
    }
    @Test
    public void test_5() throws IOException {
        testClass("Dependent1_5", 0, 0,
                new DebugConfiguration.Builder()
                        .build());
    }
    @Test
    public void test_6() throws IOException {
        testClass("Dependent1_6", 0, 0,
                new DebugConfiguration.Builder()
                        .build());
    }
}
