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

import org.e2immu.annotation.Constant;

import java.util.HashSet;
import java.util.Set;

/*
Tests the $Remove companion method
 */
public class BasicCompanionMethods_9 {

    @Constant("1")
    static int test() {
        Set<String> set = new HashSet<>();
        boolean added = set.add("a");
        assert added;                 // 2 OK
        assert set.contains("a");     // 3 OK

        boolean removed1 = set.remove("a");
        assert removed1;              // 5 OK
        assert !set.contains("a");    // 6 OK
        assert set.size() == 0;       // 7 OK
        assert set.isEmpty();         // 8 OK

        set.add("c");

        assert !set.contains("b");    // 10
        boolean removed2 = set.remove("b");
        assert !removed2;             // 12 OK

        return set.size();
    }

}
