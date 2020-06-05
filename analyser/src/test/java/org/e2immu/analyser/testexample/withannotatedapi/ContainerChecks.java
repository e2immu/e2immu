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

        @Nullable
        private Set<String> strings1;

        public void setStrings1(@Modified @NotNull Set<String> strings1param, String toAdd) {
            this.strings1 = strings1param;
            this.strings1.add(toAdd);
        }

        public Set<String> getStrings1() {
            return strings1;
        }
    }

    // second example: the add method breaks the contract
    // however, the setter may never be called
    // the @Size problem we've observed here is replicated in SizeChecks2
    @Container(type = VERIFY_ABSENT)
    static class Container2 {

        @Linked(to = "strings2param")
        @Modified
        private Set<String> strings2;

        @Modified
        public void setStrings2(@Modified Set<String> strings2param) {
            this.strings2 = strings2param;
        }

        @NotModified
        public Set<String> getStrings2() {
            return strings2;
        }

        // this method breaks the contract, in a roundabout way
        @Modified
        public void add2(@NotNull String string2) {
            strings2.add(string2);
        } // ERROR
    }

    // variant of the second example: the add method breaks the contract;
    // this works easily because strings2b is final
    @Container(type = VERIFY_ABSENT)
    @ModifiesArguments
    static class Container2b {

        @Linked(to = "strings2param")
        @Modified
        @Nullable
        private final Set<String> strings2b;

        @Modified
        public Container2b(@Modified Set<String> strings2param) {
            this.strings2b = strings2param;
        }

        @NotModified
        public Set<String> getStrings2b() {
            return strings2b;
        }

        // this method breaks the contract, in a roundabout way
        @Modified
        public void add2b(@NotNull String string2b) {
            if (strings2b != null) {
                strings2b.add(string2b);
            }
        }
    }


    // third example: independent, so this one works
    // this is not a @Container @Final @NotModified, because strings can be set multiple times, and can be modified
    @Container
    static class Container3 {

        @NotModified(type = VERIFY_ABSENT)
        @Modified
        @Variable
        @Nullable
        private Set<String> strings3;

        public void setStrings3(Set<String> strings3param) {
            this.strings3 = new HashSet<>(strings3param);
        }

        public Set<String> getStrings3() {
            return strings3;
        }

        // String is @NotModified because it is a context class
        public void add(String s3) {
            Set<String> set3 = strings3;
            if (set3 != null) set3.add(s3);
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
        public void m1(@NotModified(type = VERIFY_ABSENT) @NotNull Set<String> modified) {
            Set<String> sourceM1 = strings4;
            modified.addAll(sourceM1);
        }

        // there should be a link from modified2 to strings
        public void m2(@NotModified(type = VERIFY_ABSENT) @NotNull Set<String> modified2) {
            Set<String> toModifyM2 = modified2;
            toModifyM2.addAll(strings4);
        }

        // we link the set 'out' to the set 'in', but who cares about this? how can we use this linkage later?
        public static void crossModify(@NotNull @NotModified Set<String> in, @NotNull @NotModified(type = VERIFY_ABSENT) Set<String> out) {
            out.addAll(in);
        }
    }

    @E1Container
    @NotNull
    static class Container5 {
        @NotModified(type = VERIFY_ABSENT)
        @Modified
        @Linked(type = VERIFY_ABSENT)
        private final List<String> list;

        @Independent
        public Container5() {
            list = new ArrayList<>();
        }

        @Independent
        public Container5(Collection<String> coll5) {
            this();
            addAll(coll5);
        }

        @NotModified(type = VERIFY_ABSENT)
        public void addAll(Collection<String> collection) {
            list.addAll(collection);
        }

        @NotModified
        @Independent
        // note: a t m we do not want @NotModified on consumer, because it is @NotModified by default (functional interface)
        public void visit(Consumer<String> consumer) {
            list.forEach(consumer);
        }
    }
}
