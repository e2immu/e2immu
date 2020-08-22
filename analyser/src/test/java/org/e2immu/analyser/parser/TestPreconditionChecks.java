
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

package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
/*
https://github.com/bnaudts/e2immu/issues/10
 */
public class TestPreconditionChecks extends CommonTestRunner {

    public TestPreconditionChecks() {
        super(false);
    }

    MethodAnalyserVisitor methodAnalyserVisitor = new MethodAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo) {
            if ("either".equals(methodInfo.name)) {
                MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
                Assert.assertEquals("(not (null == e1) or not (null == e2))", methodAnalysis.precondition.get().toString());
            }
        }
    };

    // TODO we leave one error for now, to be implemented later once we have a Precondition value
    @Test
    public void test() throws IOException {
        testClass("PreconditionChecks", 1, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
