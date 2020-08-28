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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.e2immu.annotation.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class E2ImmutableChecks {

    // 1. all fields are final (explicitly)
    // 2. all fields are primitive, context class or (XXX)
    // 3. there are no non-primitive non-context class
    // 4. ditto

    @E2Container
    static class E2Container1 {
        public final int level1;
        public final String value1;

        public E2Container1(String value, int level1Param) {
            level1 = level1Param;
            this.value1 = value;
        }

        public boolean isAbc() {
            return "abc".equals(value1);
        }
    }

    // 1. all fields are final (explicitly and implicitly)
    // 2. all fields are primitive, context class or (XXX); added complication: recursive definition
    // 3. there are no non-primitive non-context class
    // 4. ditto

    @E2Container
    static class E2Container2 {
        @Nullable
        public final E2Container2 parent2;
        public int level2;
        @Nullable
        public final String value2;

        public E2Container2(String value) {
            this.parent2 = null;
            level2 = 0;
            this.value2 = value;
        }

        public E2Container2(@NotNull E2Container2 parent2Param, String valueParam2) {
            this.parent2 = parent2Param;
            level2 = parent2Param.level2 + 2;
            this.value2 = valueParam2;
        }
    }

    // 1. all fields are final (explicitly and implicitly)
    // 2. all fields are primitive, context class or: not exposed, not linked
    // 3. there are no non-primitive non-context class
    // 4. ditto
    @E2Container
    static class E2Container3 {
        @Linked(type = AnnotationType.VERIFY_ABSENT)
        private final Set<String> set3;

        public E2Container3(Set<String> set3Param) {
            set3 = new HashSet<>(set3Param); // not linked
        }

        @E2Container
        public Set<String> getSet3() {
            return ImmutableSet.copyOf(set3);
        }
    }

    static int getSize(int i) {
        return 10 + i;
    }

    @E2Immutable
    @MutableModifiesArguments //  not a @Container!
    static class E2Immutable4 {
        @E2Container
        public final Set<String> strings4;

        public E2Immutable4(@NotNull @NotModified Set<String> input4) {
            strings4 = ImmutableSet.copyOf(input4);
        }

        @E2Container
        @NotNull
        @Constant(type = AnnotationType.VERIFY_ABSENT)
        public Set<String> getStrings4() {
            return strings4;
        }

        @Identity
        @Linked(type = AnnotationType.VERIFY_ABSENT)
        @Constant(type = AnnotationType.VERIFY_ABSENT)
        public Set<String> mingle(@NotNull @NotModified(type = AnnotationType.VERIFY_ABSENT) Set<String> input4) {
            input4.addAll(strings4);
            return input4;
        }
    }

    @E2Container
    static class E2Container4 {
        @Linked(type = AnnotationType.VERIFY_ABSENT)
        private final Map<String, String> map4;

        public E2Container4(Map<String, String> map4Param) {
            map4 = new HashMap<>(map4Param); // not linked
        }

        @E2Container
        public Map<String, String> getMap4() {
            return ImmutableMap.copyOf(map4);
        }
    }
}
