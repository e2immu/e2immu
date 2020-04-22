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

import org.e2immu.annotation.*;

import java.util.*;
import java.util.function.Consumer;

import static org.e2immu.annotation.AnnotationType.VERIFY_ABSENT;

public class ContainerChecks {

    // the definition of Container is that the parameters of publicly available methods
    // are @NotModified (not modifiable) or effectively immutable (@ContextClass)

    // first example: the setter itself breaks the contract
    @Container(type = VERIFY_ABSENT)
    static class Container1 {

        private Set<String> strings;

        public void setStrings(@NotModified(type = VERIFY_ABSENT) Set<String> strings, String toAdd) {
            this.strings = strings;
            this.strings.add(toAdd);
        }

        public Set<String> getStrings() {
            return strings;
        }
    }

    // second example: the add method breaks the contract
    @Container(type = VERIFY_ABSENT)
    static class Container2 {

        private Set<String> strings;

        public void setStrings(@NotModified(type = VERIFY_ABSENT) Set<String> strings) {
            this.strings = strings;
        }

        public Set<String> getStrings() {
            return strings;
        }

        // this method breaks the contract!
        public void add(String string) {
            strings.add(string);
        }
    }

    // third example: independent, so this one works
    // this is not a @Container @Final @NotModified, because strings can be set multiple times, and can be modified
    @Container
    static class Container3 {

        @NotModified(type = VERIFY_ABSENT)
        private Set<String> strings;

        public void setStrings(@NotModified Set<String> strings) {
            this.strings = new HashSet<>(strings);
        }

        public Set<String> getStrings() {
            return strings;
        }

        // String is @NotModified because it is a context class
        public void add(String string) {
            strings.add(string);
        }
    }

    @Container(type = VERIFY_ABSENT)
    static class Container4 {

        @NotNull
        @Linked
        private final Set<String> strings4;

        public Container4(@NotNull Set<String> strings4Param) {
            this.strings4 = Objects.requireNonNull(strings4Param);
        }

        public Set<String> getStrings4() {
            return strings4;
        }

        // there should be a link from the field (or the source link, the input parameter 'strings', to 'modified'
        public void m1(@NotModified(type = VERIFY_ABSENT) Set<String> modified) {
            Set<String> sourceM1 = strings4;
            modified.addAll(sourceM1);
        }

        // there should be a link from modified2 to strings
        public void m2(@NotModified(type = VERIFY_ABSENT) Set<String> modified2) {
            Set<String> toModifyM2 = modified2;
            toModifyM2.addAll(strings4);
        }

        // we link the set 'out' to the set 'in', but who cares about this? how can we use this linkage later?
        public static void crossModify(@NotModified Set<String> in, @NotModified(type = VERIFY_ABSENT) Set<String> out) {
            out.addAll(in);
        }
    }

    @Container
    @NotNull
    static class Container5 {
        @NotModified(type = VERIFY_ABSENT)
        @Linked(type = VERIFY_ABSENT)
        private final List<String> list;

        @Independent
        public Container5() {
            list = new ArrayList<>();
        }

        @Independent
        public Container5(Collection<String> collection) {
            this();
            addAll(collection);
        }

        public void addAll(@NotModified Collection<String> collection) {
            list.addAll(collection);
        }

        @NotModified
        public void visit(Consumer<String> consumer) {
            list.forEach(consumer);
        }
    }
}
