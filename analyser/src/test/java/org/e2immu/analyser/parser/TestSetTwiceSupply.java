
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
import org.e2immu.analyser.config.TypeMapVisitor;
import org.e2immu.analyser.model.Level;
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

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("runnable".equals(d.fieldInfo().name) && d.iteration() > 0) {
            Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;
        int iteration = d.iteration();

        if ("isSetPotentiallyRun".equals(name) && iteration > 0) {
            Assert.assertTrue(d.methodAnalysis().methodLevelData().
                    getCallsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod());
        }
        if ("set".equals(name) && "SetTwiceSupply".equals(d.methodInfo().typeInfo.simpleName) && iteration > 0) {
            Assert.assertEquals("null == this.t", d.methodAnalysis().getPrecondition().toString());
        }
        if ("getPotentiallyRun".equals(name) && d.methodInfo().methodInspection.get().getParameters().size() == 0 && iteration > 0) {
            Assert.assertEquals("not (null == this.t)", d.methodAnalysis().getPrecondition().toString());
        }
        if ("getPotentiallyRun".equals(name) && d.methodInfo().methodInspection.get().getParameters().size() == 1) {
            Assert.assertEquals("not (null == this.t)", d.methodAnalysis().getPrecondition().toString());
        }
        if ("setRunnable".equals(name) && iteration > 0) {
            Assert.assertEquals("null == this.t", d.methodAnalysis().getPrecondition().toString());
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo runnable = typeMap.get(Runnable.class);
        Assert.assertTrue(runnable.typeInspection.get().isFunctionalInterface());
    };

    @Test
    public void test() throws IOException {
        testUtilClass(List.of("SetTwice", "SetTwiceSupply"), 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
