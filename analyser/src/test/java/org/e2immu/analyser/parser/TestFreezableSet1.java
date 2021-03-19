
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
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodAnalysis;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFreezableSet1 extends CommonTestRunner {

    public TestFreezableSet1() {
        super(true);
    }

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if (d.iteration() > 1) {
            assertEquals(1L, d.typeAnalysis().getApprovedPreconditionsE2().size());
            assertEquals("frozen", d.typeAnalysis().markLabel());
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        int modified = d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD);
        MethodAnalysis methodAnalysis = d.methodAnalysis();
        String name = d.methodInfo().name;
        if (d.iteration() > 0) {
            if ("stream".equals(name)) {
                assertEquals(Level.FALSE, modified);
                assertEquals("[this.frozen]", methodAnalysis.getPreconditionForEventual().toString());
            }
            if ("streamEarly".equals(name)) {
                assertEquals(Level.FALSE, modified);
                assertEquals("[not (this.frozen)]", methodAnalysis.getPreconditionForEventual().toString());
            }
            if ("add".equals(name)) {
                assertEquals(Level.TRUE, modified);
                assertEquals("[not (this.frozen)]", methodAnalysis.getPreconditionForEventual().toString());
            }
            if ("freeze".equals(name)) {
                assertEquals(Level.TRUE, modified);
                assertEquals("[not (this.frozen)]", methodAnalysis.getPreconditionForEventual().toString());
            }
            if ("isFrozen".equals(name)) {
                assertEquals(Level.FALSE, modified);
                assertTrue(methodAnalysis.getPreconditionForEventual().isEmpty());
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
