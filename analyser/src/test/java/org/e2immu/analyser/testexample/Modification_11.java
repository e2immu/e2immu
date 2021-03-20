/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.Objects;
import java.util.Set;

@Container(absent = true)
@E1Container(absent = true)
@E1Immutable
public class Modification_11 {

    @Modified
    @Linked(to = {"Modification_11:set1"})
    final Set<String> s1;

    @NotModified(absent = true)
    @Modified
    @Linked(to = {"Modification_11:set2"})
    final Set<String> s2;

    @NotModified(absent = true)
    @Modified
    @Linked(to = {"Modification_11:set3"})
    final C1 c3;

    public Modification_11(@Modified @NotModified(absent = true) Set<String> set1,
                           @Modified Set<String> set2,
                           @Modified Set<String> set3) {
        s1 = set1;
        s2 = new C1(set2).getSet();
        c3 = new C1(set3);
    }

    @E1Immutable // final fields, not all parameters @NotModified
    static class C1 {
        @Linked(to = {"C1:setC"})
        @NotNull1
        @Modified
        final Set<String> set; // linked to set1

        C1(@NotNull1 @Modified Set<String> setC) {
            this.set = Objects.requireNonNull(setC);
        }

        @NotNull1
        @NotModified // do not change the fields
        @Linked(to = {"C1.setC"})
        @Independent(absent = true)
        Set<String> getSet() {
            return set;
        }

        @Modified
        void add(String string) {
            set.add(string);
        }
    }

    @Modified
    @Linked(absent = true) // primitive
    public int add(@NotNull String s) {
        Set<String> theSet = s1; // linked to s2, which is linked to set2
        System.out.println("The set has " + theSet.size() + " elements before adding " + s);
        theSet.add(s); // this one modifies set2!
        return example1() || example2() ? 1 : 0; // this one modifies set3!
    }

    @Linked(absent = true) // primitive
    private boolean example1() {
        C1 c = new C1(s2); // c.set is linked to s0 which is linked to set3
        C1 localD = new C1(Set.of("a", "b", "c"));
        return addAll(c.set, localD.set); // c cannot be @NotModified because of the addAll call
    }

    @Linked(absent = true) // primitive
    @Modified
    private boolean example2() {
        C1 d = new C1(Set.of("d"));
        return addAllOnC(c3, d);
    }

    // this is an extension function on Set
    @Linked(absent = true) // primitive
    private static boolean addAll(@NotNull @Modified Set<String> c, @NotNull1 @NotModified Set<String> d) {
        return c.addAll(d);
    }

    // this is an extension function on C1
    // not modified applies to sub-fields as well.
    @Linked(absent = true) // primitive
    private static boolean addAllOnC(@NotNull @Modified C1 c1, @NotNull @NotModified C1 d1) {
        return c1.set.addAll(d1.set);
    }
}
