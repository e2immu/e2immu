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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_01_Loops extends CommonTestRunner {

    public Test_01_Loops() {
        super(true);
    }

    @Test
    public void test0() throws IOException {

        testClass("Loops_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test1() throws IOException {

        testClass("Loops_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test2() throws IOException {

        testClass("Loops_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test3() throws IOException {

        testClass("Loops_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test4() throws IOException {

        testClass("Loops_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test5() throws IOException {

        testClass("Loops_5", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test6() throws IOException {

        testClass("Loops_6", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test7() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                Assert.assertNotNull(d.haveError(Message.LOOP_WITHOUT_MODIFICATION));
            }
        };
        testClass("Loops_7", 3, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
