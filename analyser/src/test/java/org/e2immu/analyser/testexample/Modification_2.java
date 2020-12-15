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

import org.e2immu.annotation.*;

import java.util.HashSet;
import java.util.Set;

public class Modification_2 {

    // quick check: the set2 field is not final now, public!

    @Container
    static class Example2bis {
        @Variable
        public Set<String> set2bis = new HashSet<>(); // ERROR: non-private field not effectively final

        @NotModified
        int size2() {
            return set2bis.size(); // WARN: potential null pointer exception!
        }
    }

    @E1Container
    static class Example2ter {
        @Modified
        private final Set<String> set2ter = new HashSet<>();

        @Modified
        public void add(String s) {
            set2ter.add(s);
        }

        @NotModified
        public String getFirst(String s) {
            return set2ter.isEmpty() ? "" : set2ter.stream().findAny().orElseThrow();
        }
    }

}
