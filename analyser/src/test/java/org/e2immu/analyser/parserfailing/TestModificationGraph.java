
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

package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestModificationGraph extends CommonTestRunner {

    public TestModificationGraph() {
        super(false);
    }

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("c1".equals(d.fieldInfo().name)) {
            int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);
            int expect = d.iteration() < 2 ? Level.DELAY : Level.TRUE;
            assertEquals(expect, modified);
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;
        if ("incrementAndGetWithI".equals(name)) {
            assertTrue(d.methodAnalysis().methodLevelData()
                    .getCallsPotentiallyCircularMethod());
        }
        if ("useC2".equals(name) && d.iteration() > 1) {
            assertTrue(d.methodAnalysis().methodLevelData()
                    .getCallsPotentiallyCircularMethod());
        }
        if ("C2".equals(name)) {
            ParameterInfo c1 = d.methodInfo().methodInspection.get().getParameters().get(1);
            if (d.iteration() > 0) {
                Map.Entry<FieldInfo, ParameterAnalysis.AssignedOrLinked> entry = c1.parameterAnalysis.get()
                        .getAssignedToField().entrySet().stream().findFirst().orElseThrow();
                assertEquals("c1", entry.getKey().name);
                assertEquals("c1", entry.getKey().name);
                if (d.iteration() > 1) {
                    assertTrue(c1.parameterAnalysis.get().isAssignedToFieldDelaysResolved());
                }
            }
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if ("C1".equals(d.typeInfo().simpleName)) {
            assertEquals(2, d.typeInfo().typeResolution.get().circularDependencies().size());
        }
        if ("C2".equals(d.typeInfo().simpleName)) {
            assertEquals(2, d.typeInfo().typeResolution.get().circularDependencies().size());
            assertEquals("[]", d.typeAnalysis().getTransparentTypes().toString());
        }
    };

    // expect one warning for circular dependencies

    @Test
    public void test() throws IOException {
        testClass(List.of("ModificationGraph", "ModificationGraphC1", "ModificationGraphC2"),
                0, 1,
                new DebugConfiguration.Builder()
                        .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().build(),
                new AnnotatedAPIConfiguration.Builder().build());

    }

}
