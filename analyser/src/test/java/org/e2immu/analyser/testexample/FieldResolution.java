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

package org.e2immu.analyser.testexample;

public class FieldResolution {

    static class C1 {
        public final String s1;

        public C1(String in1, C2 c2) {
            s1 = in1 + c2.prefix2;
        }
    }

    static class C2 {
        public final String prefix2;

        public C2(String in2) {
            prefix2 = in2;
        }

        public String withC1(C1 c1) {
            return c1.s1 + prefix2;
        }
    }
}
