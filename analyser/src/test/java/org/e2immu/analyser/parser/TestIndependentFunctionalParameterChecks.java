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
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestIndependentFunctionalParameterChecks extends CommonTestRunner {

    public TestIndependentFunctionalParameterChecks() {
        super(true);
    }

    // the @NotNull1 on stream() is only known after the first iteration
    // it should not yet cause an error in the first.
    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if("getFirst".equals(d.methodInfo.name) && d.iteration == 0) {
            Assert.assertFalse(d.statementAnalysis.errorFlags.errorValue.isSet());
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("stream".equals(d.methodInfo().name)) {
            int notNull = d.methodAnalysis().getProperty(VariableProperty.NOT_NULL);
            if (d.iteration() == 0) {
                Assert.assertEquals(Level.DELAY, notNull);
            } else {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, notNull);
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("IndependentFunctionalParameterChecks", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
