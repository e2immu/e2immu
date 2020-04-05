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

import static org.e2immu.annotation.AnnotationType.*;

import java.util.*;

public class NotModifiedChecks {
    @NotModified
    @Linked(to = {"NotModifiedChecks.list"})
    final Collection<String> c0;

    @NotModified
    @Linked(to = {"NotModifiedChecks.list"})
    final Collection<String> c1;

    @NotModified(type = VERIFY_ABSENT)
    @Linked(to = {"NotModifiedChecks.set3"})
    final Set<String> s0;

    @NotModified
    @Linked(type = VERIFY_ABSENT)
    final Set<String> s1;

    @NotModified(type = VERIFY_ABSENT)
    @Linked(to = {"NotModifiedChecks.set2"})
    final Set<String> s2;

    @NotModified(type = VERIFY_ABSENT)
    @Linked(to = {"NotModifiedChecks.set4"})
    final C1 c4;

    @Linked(type = VERIFY_ABSENT)
    final int l0;

    @Linked(type = VERIFY_ABSENT)
    final int l1;

    @Linked(type = VERIFY_ABSENT)
    final int l2;

    public NotModifiedChecks(@NotModified List<String> list,
                             @NotModified(type = VERIFY_ABSENT) Set<String> set2,
                             @NotModified(type = VERIFY_ABSENT) Set<String> set3,
                             @NotModified(type = VERIFY_ABSENT) Set<String> set4) {
        c0 = list;
        c1 = list.subList(0, list.size() / 2);
        s0 = set3;
        s1 = new HashSet<>(list); // we do not want to link s1 to list, new HashSet<> clones!
        s2 = new C1(set2).getSet();
        c4 = new C1(set4);
        l0 = list.size();
        l1 = 4;
        l2 = l0 + l1;
    }

    static class C1 {
        @Linked(to = {"C1.set1"})
        final Set<String> set; // linked to set1

        C1(@NullNotAllowed Set<String> set1) {
            this.set = Objects.requireNonNull(set1);
        }

        @NotNull
        @Linked(to = {"C1.set1"})
        Set<String> getSet() {
            return set;
        }
    }

    @NotModified
    @Linked(to = {"NotModifiedChecks.list"})
    public Collection<String> getC0() {
        return c0;
    }

    @NotModified(type = VERIFY_ABSENT)
    @Linked(type = VERIFY_ABSENT) // primitive
    public int add(String s) {
        Set<String> theSet = s2; // linked to s2, which is linked to set2
        System.out.println("The set has " + theSet.size() + " elements before adding " + s);
        theSet.add(s); // this one modifies set2!
        return example1() || example2() ? 1 : 0; // this one modifies set3!
    }

    @Linked(type = VERIFY_ABSENT) // primitive
    private boolean example1() {
        C1 c = new C1(s0); // c.set is linked to s0 which is linked to set3
        C1 d = new C1(Set.of("a", "b", "c"));
        return addAll(c.set, d.set); // c cannot be @NotModified because of the addAll call
    }

    @Linked(type = VERIFY_ABSENT) // primitive
    private boolean example2() {
        C1 d = new C1(Set.of("d"));
        return addAllOnC(c4, d);
    }

    // this is an extension function on Set
    @Linked(type = VERIFY_ABSENT) // primitive
    private static boolean addAll(@NotModified(type = VERIFY_ABSENT) Set<String> c,
                                  @NotModified Set<String> d) {
        return c.addAll(d);
    }

    // this is an extension function on C1
    // not modified applies to sub-fields as well.
    @Linked(type = VERIFY_ABSENT) // primitive
    private static boolean addAllOnC(@NotModified(type = VERIFY_ABSENT) C1 c,
                                     @NotModified C1 d) {
        return c.set.addAll(d.set);
    }
}
