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
    // 2. all non-private fields are primitive

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
    // 2. all non-private fields are primitive + added complication: recursive definition

    @E2Container
    static class E2Container2 {
        @Nullable
        public final E2Container2 parent2;
        @Final
        private int level2;
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

        public int getLevel2() {
            return level2;
        }
    }

    // 1. all fields are final
    // 2. all fields are not modified
    // 3. fields are private
    // 4. constructor and method are independent
    @E2Container
    static class E2Container3 {
        @Linked(absent = true)
        @NotModified
        private final Set<String> set3;

        @Independent
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
    static class E2Immutable4 {
        @E2Container
        @NotNull1
        public final Set<String> strings4;

        @Independent
        public E2Immutable4(@NotNull @NotModified Set<String> input4) {
            strings4 = ImmutableSet.copyOf(input4);
        }

        @E2Container
        @NotNull1
        @Constant(absent = true)
        public Set<String> getStrings4() {
            return strings4;
        }

        @Identity
        @Linked(absent = true)
        @Constant(absent = true)
        public Set<String> mingle(@NotNull @Modified Set<String> input4) {
            input4.addAll(strings4);
            return input4;
        }
    }

    @E2Container
    static class E2Container4 {
        @Linked(absent = true)
        private final Map<String, String> map4;

        public E2Container4(Map<String, String> map4Param) {
            map4 = new HashMap<>(map4Param); // not linked
        }

        public String get4(String input) {
            return map4.get(input);
        }

        @E2Container
        public Map<String, String> getMap4() {
            return ImmutableMap.copyOf(map4);
        }
    }


    @E2Container
    static class E2Container5<T> {
        @Linked(absent = true)
        private final Map<String, T> map5;

        public E2Container5(Map<String, T> map5Param) {
            map5 = new HashMap<>(map5Param); // not linked
        }

        public T get5(String input) {
            return map5.get(input);
        }

        @E2Container
        public Map<String, T> getMap5() {
            return ImmutableMap.copyOf(map5);
        }
    }

    @Independent @Container
    static class SimpleContainer {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    // here, SimpleContainer can be replaced by T or Object
    @E2Container
    static class E2Container6 {
        private final Map<String, SimpleContainer> map6;

        public E2Container6(Map<String, SimpleContainer> map6Param) {
            map6 = new HashMap<>(map6Param); // not linked
        }

        public SimpleContainer get6(String input) {
            return map6.get(input);
        }

        @E2Container
        public Map<String, SimpleContainer> getMap6() {
            return ImmutableMap.copyOf(map6);
        }
    }

    // here, SimpleContainer cannot be replaced by T or Object, but there are no fields linked to parameters
    @E1Container
    @Independent
    static class E1Container7 {
        @NotModified
        private final Map<String, SimpleContainer> map7;

        @Independent
        public E1Container7(Map<String, SimpleContainer> map7Param) {
            map7 = new HashMap<>(map7Param); // not linked
        }

        @Dependent
        public SimpleContainer get7(String input) {
            return map7.get(input);
        }

        @Independent
        public Map<String, SimpleContainer> getMap7() {
            Map<String, SimpleContainer> incremented = new HashMap<>(map7);
            incremented.values().forEach(sc -> sc.setI(sc.getI() + 1));
            return incremented;
        }
    }

    // here, SimpleContainer cannot be replaced by T or Object, map8 is linked to the parameter
    @E1Container
    @Independent(absent = true)
    static class E1Container8 {
        @NotModified
        private final Map<String, SimpleContainer> map8;

        @Dependent
        public E1Container8(Map<String, SimpleContainer> map8Param) {
            map8 = map8Param; // linked
        }

        @Dependent
        public SimpleContainer get8(String input) {
            return map8.get(input);
        }

        @Independent
        public Map<String, SimpleContainer> getMap8() {
            Map<String, SimpleContainer> incremented = new HashMap<>(map8);
            incremented.values().forEach(sc -> sc.setI(sc.getI() + 1));
            return incremented;
        }
    }
}
