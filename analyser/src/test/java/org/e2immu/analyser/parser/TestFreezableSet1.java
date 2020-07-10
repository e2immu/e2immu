
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
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.TypeAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.objectflow.*;
import org.e2immu.analyser.testexample.withannotatedapi.ObjectFlow2;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TestFreezableSet1 extends CommonTestRunner {

    public TestFreezableSet1() {
        super(true);
    }

    TypeAnalyserVisitor typeAnalyserVisitor = new TypeAnalyserVisitor() {
        @Override
        public void visit(int iteration, TypeInfo typeInfo) {
            if (iteration > 0) {
                Assert.assertEquals("m0", typeInfo.typeAnalysis.get().approvedPreconditions.stream().findFirst().orElseThrow().getValue());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            int modified = methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
            if (iteration > 0) {
                if ("stream".equals(methodInfo.name)) {
                    Assert.assertEquals(Level.FALSE, modified);
                    Assert.assertEquals("frozen", methodAnalysis.preconditionForOnlyData.get().toString());
                }
                if ("streamEarly".equals(methodInfo.name)) {
                    Assert.assertEquals(Level.FALSE, modified);
                    Assert.assertEquals("not (frozen)", methodAnalysis.preconditionForOnlyData.get().toString());
                }
                if ("add".equals(methodInfo.name)) {
                    Assert.assertEquals(Level.TRUE, modified);
                    Assert.assertEquals("not (frozen)", methodAnalysis.preconditionForOnlyData.get().toString());
                }
                if ("freeze".equals(methodInfo.name)) {
                    Assert.assertEquals(Level.TRUE, modified);
                    Assert.assertEquals("not (frozen)", methodAnalysis.preconditionForOnlyData.get().toString());
                }
                if ("isFrozen".equals(methodInfo.name)) {
                    Assert.assertEquals(Level.FALSE, modified);
                    Assert.assertSame(UnknownValue.NO_VALUE, methodAnalysis.preconditionForOnlyData.get());
                }
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("FreezableSet1", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());

    }

}
