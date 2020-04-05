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

package org.e2immu.analyser.testexample.withannotatedapi;

import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NullNotAllowed;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;
import static org.e2immu.annotation.AnnotationType.*;

public class SimpleNotModifiedChecks {

    static class Example1 {
        @NotModified(type = VERIFY_ABSENT)
        public Set<String> set1 = new HashSet<>();

        public void add(String v) {
            set1.add(v);
        }
    }

    static class Example2 {
        @NotModified
        public Set<String> set2 = new HashSet<>();
    }

    static class Example3 {
        @NotModified(type = VERIFY_ABSENT)
        public Set<String> set3 = new HashSet<>();

        public void add3(String v) {
            Set<String> local3 = set3;
            local3.add(v);
        }
    }

    static class Example4 {
        @NotModified(type = VERIFY_ABSENT)
        public Set<String> set4;

        public Example4(@NotModified(type = VERIFY_ABSENT) Set<String> in4) {
            this.set4 = in4;
        }

        public void add4(String v) {
            Set<String> local4 = set4;
            local4.add(v);
        }
    }

    static class Example5 {
        @NotModified(type = VERIFY_ABSENT)
        public Set<String> set5;

        public Example5(@NotModified Set<String> in5) {
            this.set5 = new HashSet<>(in5);
        }

        public void add5(String v) {
            Set<String> local5 = set5;
            local5.add(v);
        }
    }

    static class Example6 {
        @NotModified(type = VERIFY_ABSENT)
        public Set<String> set6 = new HashSet<>();

        public static void copyIn(@NullNotAllowed Example6 example6, @NullNotAllowed @NotModified Set<String> values) {
            example6.set6.addAll(values);
        }
    }

    // somehow we should add the @NotModified(absent=true)
    static Function<Set<String>, Set<String>> removeElement = set -> {
        Iterator<String> it = set.iterator();
        if (it.hasNext()) it.remove();
        return set;
    };

    @FunctionalInterface
    interface RemoveOne {
        @Fluent
        Set<String> go(@NullNotAllowed @NotModified(type = VERIFY_ABSENT) Set<String> in);
    }

    static RemoveOne removeOne = set -> {
        Iterator<String> it = set.iterator();
        if (it.hasNext()) it.remove();
        return set;
    };
}
