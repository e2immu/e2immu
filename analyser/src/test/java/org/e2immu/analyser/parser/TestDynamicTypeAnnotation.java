
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
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestDynamicTypeAnnotation extends CommonTestRunner {

    public TestDynamicTypeAnnotation() {
        super(true);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if (d.iteration() > 0) {
            if ("modifySetCreated".equals(d.methodInfo().name)) {
                Assert.assertNotNull(d.haveError(Message.CALLING_MODIFYING_METHOD_ON_E2IMMU));
            }
            if ("modifySet1".equals(d.methodInfo().name)) {
                Assert.assertNotNull(d.haveError(Message.CALLING_MODIFYING_METHOD_ON_E2IMMU));
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("set1".equals(d.fieldInfo().name) && d.iteration() > 0) {
            int size = d.fieldInfo().fieldAnalysis.get().getProperty(VariableProperty.SIZE);
            Assert.assertEquals(Level.encodeSizeEquals(2), size);
        }
    };

    @Test
    public void test() throws IOException {
        testClass("DynamicTypeAnnotation", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
