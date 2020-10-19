
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
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

// in this test, the introduction of a functional interface has resolved the circular dependency
// however, useC2 remains modifying because of the functional interface

public class TestModificationGraphInterface extends CommonTestRunner {

    public TestModificationGraphInterface() {
        super(false);
    }

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("useC2".equals(methodInfo.name)) {
            Assert.assertTrue(methodInfo.methodAnalysis.get().methodLevelData()
                    .callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.get());
        }
        if ("incrementAndGetWithI".equals(methodInfo.name) && iteration > 0) {
            Assert.assertEquals(Level.TRUE, methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("c1".equals(d.fieldInfo().name) && d.iteration() > 1) {
            Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED));
        }
    };

    @Test
    public void test() throws IOException {
        testClass(List.of("ModificationGraphInterface", "ModificationGraphInterfaceC1", "ModificationGraphInterfaceC2",
                "ModificationGraphInterfaceIncrementer"), 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().build());
    }

}
