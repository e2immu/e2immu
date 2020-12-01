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
Tests the $Value$Size companion method on contains and isEmpty
 */
public class BasicCompanionMethods_8 {

    @Constant("1")
    static int test() {
        Set<String> set = new HashSet<>();
        assert !set.contains("b"); // always true   1 - OK

        boolean added1 = set.add("a");
        assert added1; // always true               3 - OK
        assert !set.isEmpty(); // always true       4 OK

        set.clear();
        assert set.isEmpty(); // always true        6 OK

        boolean added2 = set.add("a");
        assert added2;  // always true              8 - OK
        boolean added3 = set.add("a");
        assert !added3;  // always true             10 -OK

        assert set.contains("a"); // always true    11 - OK
        assert !set.contains("b"); // always true   12 OK

        return set.size();
    }

}
