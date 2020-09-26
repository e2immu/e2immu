
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
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class TestSetTwiceSupply extends CommonTestRunner {

    // !! BE CAREFUL, SetTwiceSupply inherits from SetTwice, and the same method name is present multiple times

    public TestSetTwiceSupply() {
        super(true);
    }

    FieldAnalyserVisitor fieldAnalyserVisitor = new FieldAnalyserVisitor() {
        @Override
        public void visit(int iteration, FieldInfo fieldInfo) {
            if ("runnable".equals(fieldInfo.name) && iteration > 0) {
                Assert.assertEquals(Level.FALSE, fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("isSetPotentiallyRun".equals(methodInfo.name) && iteration > 0) {
            Assert.assertTrue(methodInfo.methodAnalysis.get().methodLevelData().
                    callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.get());
        }
        if ("set".equals(methodInfo.name) && "SetTwiceSupply".equals(methodInfo.typeInfo.simpleName) && iteration > 0) {
            Assert.assertEquals("null == this.t", methodInfo.methodAnalysis.get().precondition.get().toString());
        }
        if ("getPotentiallyRun".equals(methodInfo.name) && methodInfo.methodInspection.get().parameters.size() == 0 && iteration > 0) {
            Assert.assertEquals("not (null == this.t)", methodInfo.methodAnalysis.get().precondition.get().toString());
        }
        if ("getPotentiallyRun".equals(methodInfo.name) && methodInfo.methodInspection.get().parameters.size() == 1) {
            Assert.assertEquals("not (null == this.t)", methodInfo.methodAnalysis.get().precondition.get().toString());
        }
        if("setRunnable".equals(methodInfo.name) && iteration>0) {
            Assert.assertEquals("null == this.t", methodInfo.methodAnalysis.get().precondition.get().toString());
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo runnable = typeContext.getFullyQualified(Runnable.class);
        Assert.assertTrue(runnable.isFunctionalInterface());
    };

    @Test
    public void test() throws IOException {
        testUtilClass(List.of("SetTwice", "SetTwiceSupply"), 0, 0, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
