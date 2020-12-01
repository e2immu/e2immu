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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/*
Tests the $Clear on other methods... we need to know when to stop
 */
public class BasicCompanionMethods_10 {

    @Constant(absent = true)
    static int test(Collection<String> in) {
        Set<String> set = new HashSet<>();
        boolean added = set.add("a");
        assert added;
        assert set.contains("a"); // true
        assert !set.contains("b");// true

        set.addAll(in);

        assert set.contains("a"); // unknown, that's a shame
        assert !set.contains("b");// unknown, that's OK

        return set.size(); // not a constant
    }

}
