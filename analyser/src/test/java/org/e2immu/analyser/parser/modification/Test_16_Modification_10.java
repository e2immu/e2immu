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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_16_Modification_10 extends CommonTestRunner {

    public Test_16_Modification_10() {
        super(true);
    }

    @Test
    public void test10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("addAll".equals(d.methodInfo().name) && "d".equals(d.variableName())) {
                assertEquals(DV.FALSE_DV, d.getProperty(Property.MODIFIED_VARIABLE));
            }
            if ("addAll".equals(d.methodInfo().name) && "c".equals(d.variableName())) {
                assertEquals(DV.TRUE_DV, d.getProperty(Property.MODIFIED_VARIABLE));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int iteration = d.iteration();

            if ("Modification_10".equals(d.methodInfo().name)) {
                ParameterAnalysis list = d.parameterAnalyses().get(0);
                ParameterAnalysis set3 = d.parameterAnalyses().get(1);

                if (iteration == 0) {
                    assertFalse(list.isAssignedToFieldDelaysResolved());
                } else {
                    assertTrue(list.isAssignedToFieldDelaysResolved());
                    assertEquals("c0=1, c1=2, l0=100, l1=100, l2=100, s0=100, s1=100", list.getAssignedToField()
                            .entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).sorted().collect(Collectors.joining(", ")));
                }
                if (iteration >= 2) {
                    assertEquals(DV.FALSE_DV, list.getProperty(Property.MODIFIED_VARIABLE));
                    assertFalse(set3.getAssignedToField().isEmpty());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            int iteration = d.iteration();
            FieldInfo fieldInfo = d.fieldInfo();
            if ("c0".equals(fieldInfo.name)) {
                if (iteration >= 2) {
                    assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.MODIFIED_OUTSIDE_METHOD));
                }
            }
            if ("s0".equals(fieldInfo.name)) {
                if (iteration >= 2) {
                    assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.MODIFIED_OUTSIDE_METHOD));
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);

            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            assertEquals(DV.TRUE_DV, addAll.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));

            ParameterInfo first = addAll.methodInspection.get().getParameters().get(0);
            assertEquals(DV.FALSE_DV, first.parameterAnalysis.get().getProperty(Property.MODIFIED_VARIABLE));

        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Modification_10".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getTransparentTypes().isEmpty());
            }
        };

        testClass("Modification_10", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
