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

package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.*;

import java.util.*;

@E1Container
public class Modification_10 {
    @NotModified
    @Linked(to = { "Modification_10:list", "Modification_10.c1" })
    final Collection<String> c0;

    @NotModified
    @Linked(to = { "Modification_10:list", "Modification_10.c0" })
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
    public Collection<String> getC0() {
        return c0;
    }

    // this is an extension function on Set
    private static boolean addAll(@NotNull @Modified Set<String> c, @NotNull1 @NotModified Set<String> d) {
        return c.addAll(d);
    }
}
