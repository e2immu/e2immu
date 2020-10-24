
/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.ParameterInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class TestModificationGraph extends CommonTestRunner {

    public TestModificationGraph() {
        super(false);
    }

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("c1".equals(d.fieldInfo().name)) {
            int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED);
            int expect = d.iteration() < 2 ? Level.DELAY : Level.TRUE;
            Assert.assertEquals(expect, modified);
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;
        if ("incrementAndGetWithI".equals(name)) {
            Assert.assertTrue(d.methodAnalysis().methodLevelData()
                    .callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.get());
        }
        if ("useC2".equals(name) && d.iteration() > 1) {
            Assert.assertTrue(d.methodAnalysis().methodLevelData()
                    .callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.get());
        }
        if ("C2".equals(name)) {
            ParameterInfo c1 = d.methodInfo().methodInspection.get().parameters.get(1);
            if (d.iteration() > 0) {
                Assert.assertEquals("c1", c1.parameterAnalysis.get().getAssignedToField().name);
                if (d.iteration() > 1) {
                    Assert.assertTrue(c1.parameterAnalysis.get().isCopiedFromFieldToParameters());
                }
            }
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if ("C1".equals(d.typeInfo().simpleName)) {
            Assert.assertEquals(2, d.typeInfo().typeResolution.get().circularDependencies.get().size());
        }
        if ("C2".equals(d.typeInfo().simpleName)) {
            Assert.assertEquals(2, d.typeInfo().typeResolution.get().circularDependencies.get().size());
            Assert.assertEquals("[]", d.typeAnalysis().getImplicitlyImmutableDataTypes().toString());
        }
    };

    // expect one warning for circular dependencies

    @Test
    public void test() throws IOException {
        testClass(List.of("ModificationGraph", "ModificationGraphC1", "ModificationGraphC2"),
                0, 1, new DebugConfiguration.Builder()
                        .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(), new AnalyserConfiguration.Builder().build());

    }

}
