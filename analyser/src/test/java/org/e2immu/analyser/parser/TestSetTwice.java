
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
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class TestSetTwice extends CommonTestRunner {


    TypeAnalyserVisitor typeAnalyserVisitor = (iteration, typeInfo) -> {
        if (iteration > 0) {
            // Assert.assertEquals(2, typeInfo.typeAnalysis.get().approvedPreconditions.size());
        }
    };

    private static final String PRECONDITION = "(not (this.overwritten) and not (null == this.t))";

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if (Set.of("overwrite", "freeze").contains(methodInfo.name) && iteration > 1) {
            Assert.assertEquals(PRECONDITION, methodInfo.methodAnalysis.get().precondition.get().toString());
            Assert.assertEquals(PRECONDITION, methodInfo.methodAnalysis.get().preconditionForMarkAndOnly.get().toString());
        }
        if ("set".equals(methodInfo.name) && iteration > 0) {
            Assert.assertEquals("null == this.t", methodInfo.methodAnalysis.get().preconditionForMarkAndOnly.get().toString());
        }
        if ("get".equals(methodInfo.name) && iteration > 0) {
            Assert.assertEquals("not (null == this.t)", methodInfo.methodAnalysis.get().preconditionForMarkAndOnly.get().toString());
        }
    };


    @Test
    public void test() throws IOException {
        testUtilClass(List.of("SetTwice"), 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

}
