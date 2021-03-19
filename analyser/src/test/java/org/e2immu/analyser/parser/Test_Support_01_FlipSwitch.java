
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
import org.e2immu.analyser.config.TypeAnalyserVisitor;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class Test_Support_01_FlipSwitch extends CommonTestRunner {

    public Test_Support_01_FlipSwitch() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("FlipSwitch".equals(d.typeInfo().simpleName)) {
                String expectE2 = d.iteration() <= 1 ? "{}" : "{t=!t}";
                Assert.assertEquals(expectE2, d.typeAnalysis().getApprovedPreconditionsE2().toString());
            }
        };

        testSupportClass(List.of("FlipSwitch"), 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                        .build());
    }

}
