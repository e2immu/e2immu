
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
import org.junit.Test;

import java.io.IOException;

public class Test_35_EventuallyImmutableUtil extends CommonTestRunner {

    public Test_35_EventuallyImmutableUtil() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("EventuallyImmutableUtil_0", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("EventuallyImmutableUtil_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("EventuallyImmutableUtil_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("EventuallyImmutableUtil_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
