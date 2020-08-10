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

import org.e2immu.annotation.*;

import java.util.HashSet;
import java.util.Set;

import static org.e2immu.annotation.AnnotationType.VERIFY_ABSENT;

public class SimpleNotModifiedChecks {

    // first example shows direct modification

    @E1Container
    static class Example1 {

        @NotModified(type = VERIFY_ABSENT)
        public Set<String> set1 = new HashSet<>();

        @NotModified(type = VERIFY_ABSENT)
        public void add(@NotNull String v) {
            set1.add(v);
        }
    }

    // second example shows no modification

    @E1Container
    static class Example2 {
        @NotModified
        public Set<String> set2 = new HashSet<>();

        @NotModified
        int size() {
            return set2.size();
        }
    }

    // third example shows modification, with a local variable as an indirection step

    @E1Container
    static class Example3 {
        @NotNull
        @NotModified(type = VERIFY_ABSENT)
        public Set<String> set3 = new HashSet<>();

        @NotModified(type = VERIFY_ABSENT)
        public void add3(@NotNull String v) {
            Set<String> local3 = set3;
            local3.add(v);
        }
    }

    // fourth example shows the same indirect modification, with a set now linked to a
    // parameter which also becomes not modified

    // in4 can be modified by calling add4, so not a container
    @E1Immutable
    @Container(type = VERIFY_ABSENT)
    static class Example4 {
        @NotModified(type = VERIFY_ABSENT)
        @NotNull
        public Set<String> set4;

        public Example4(@NotModified(type = VERIFY_ABSENT) @NotNull Set<String> in4) {
            this.set4 = in4;
        }

        @NotModified(type = VERIFY_ABSENT)
        public void add4(@NotNull String v) {
            Set<String> local4 = set4;
            local4.add(v); // this statement induces a @NotNull on in4
        }
    }

    // fifth example shows the same indirect modification; this time construction is not
    // linked to the set

    @E1Container
    static class Example5 {
        @NotModified(type = VERIFY_ABSENT)
        public Set<String> set5;

        public Example5(@NotModified Set<String> in5) {
            this.set5 = new HashSet<>(in5);
        }

        @NotModified(type = VERIFY_ABSENT)
        public void add5(String v) {
            Set<String> local5 = set5;
            local5.add(v);
        }
    }

    // sixth example is direct modification, but indirectly on an instance variable of the class

    @E1Immutable
    @Container(type = VERIFY_ABSENT)
    static class Example6 {
        @NotModified(type = VERIFY_ABSENT)
        @NotNull
        public Set<String> set6;

        public Example6(@NotModified(type = VERIFY_ABSENT) @NotNull Set<String> in6) {
            this.set6 = in6;
        }

        @NotModified(type = VERIFY_ABSENT)
        public static void add6(@NotNull Example6 example6, @NotNull @NotModified Set<String> values6) {
            example6.set6.addAll(values6);
        }
    }
}
