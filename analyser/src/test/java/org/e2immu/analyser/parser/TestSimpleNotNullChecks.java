/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.StringValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/*

https://github.com/bnaudts/e2immu/issues/8

 */
public class TestSimpleNotNullChecks extends CommonTestRunner {
    public TestSimpleNotNullChecks() {
        super(true);
    }

    MethodAnalyserVisitor methodAnalyserVisitor = (iteration, methodInfo) -> {
        if ("conditionalValue".equals(methodInfo.name) && iteration > 0) {
            Value srv = methodInfo.methodAnalysis.get().singleReturnValue.get();
            Assert.assertEquals("condition.test(initial)?alternative:initial", srv.toString());
        }
    };

    @Test
    public void test() throws IOException {
        testClass("SimpleNotNullChecks", 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
