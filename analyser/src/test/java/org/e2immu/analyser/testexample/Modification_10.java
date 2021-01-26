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

import java.util.*;

@E1Container
public class Modification_10 {
    @NotModified
    @Linked(to = {"Modification_10:list"})
    final Collection<String> c0;

    @NotModified
    @Linked(to = {"Modification_10:list"})
    final Collection<String> c1;

    @NotModified
    @Linked(to = {"Modification_10:set3"})
    final Set<String> s0;

    @NotModified
    @Modified(absent = true)
    @Linked(absent = true)
    final Set<String> s1;

    @Linked(absent = true)
    final int l0;

    @Linked(absent = true)
    final int l1;

    @Linked(absent = true)
    final int l2;

    public Modification_10(@NotModified @NotNull1 List<String> list,
                           @NotModified Set<String> set3) {
        c0 = list;
        c1 = list.subList(0, list.size() / 2);
        s0 = set3;
        s1 = new HashSet<>(list); // we do not want to link s1 to list, new HashSet<> clones!
        l0 = list.size();
        l1 = 4;
        l2 = l0 + l1;
    }

    @NotModified
    @Linked(to = {"NotModifiedChecks.list"})
    public Collection<String> getC0() {
        return c0;
    }

    // this is an extension function on Set
    @Linked(absent = true) // primitive
    private static boolean addAll(@NotNull @Modified Set<String> c, @NotNull1 @NotModified Set<String> d) {
        return c.addAll(d);
    }
}
