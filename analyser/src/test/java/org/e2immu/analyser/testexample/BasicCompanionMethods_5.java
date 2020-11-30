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

public class BasicCompanionMethods_5 {

    @Constant("true")
    static boolean test() {
        Set<String> set = new HashSet<>();
        set.add("a");
        assert set.contains("a");
        assert set.size() == 1;

        set.add("a");
        assert set.contains("a");
        assert set.size() == 1;

        set.add("b");
        assert set.contains("a");
        assert set.contains("b");
        return set.size() == 2;
    }

}
