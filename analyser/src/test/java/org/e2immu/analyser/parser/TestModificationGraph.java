
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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.TypeAnalyserVisitor;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.stream.Collectors;

public class TestModificationGraph extends CommonTestRunner {

    public TestModificationGraph() {
        super(false);
    }

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("incrementAndGetWithI".equals(methodInfo.name)) {
            Assert.assertEquals("C1", methodInfo.methodAnalysis.get().typesModified
                    .stream().map(e -> e.getKey().simpleName).sorted().collect(Collectors.joining(",")));
        }
        if ("useC2".equals(methodInfo.name) && iteration > 1) {
            // Assert.assertEquals("C2", methodInfo.methodAnalysis.get().typesModified
            //        .stream().map(e -> e.getKey().simpleName).sorted().collect(Collectors.joining(",")));
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = (iteration, typeInfo) -> {
        if ("C1".equals(typeInfo.simpleName)) {
            Assert.assertEquals(2, typeInfo.typeAnalysis.get().circularDependencies.get().size());
        }
        if ("C2".equals(typeInfo.simpleName)) {
            Assert.assertEquals(2, typeInfo.typeAnalysis.get().circularDependencies.get().size());
        }
    };

    @Test
    public void test() throws IOException {
        testClass("ModificationGraph", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());

    }

}
