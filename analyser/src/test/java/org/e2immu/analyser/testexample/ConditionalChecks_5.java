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

package org.e2immu.analyser.testexample;

public class ConditionalChecks_5 {

    static void method(int p, int q) {
        String s = null;
        if (p < 3) {
            if (q > 4) {
                s = "abc";
            } else {
                s = "xyz";
            }
        } else {
            if (q < 0) {
                s = "tuv";
            }
        }
        assert s != null; // should not always be true; depends on p and q
    }

}
