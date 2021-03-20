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

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@E2Container(after = "frozen")
public class FreezableSet1 {

    private final Set<String> set = new HashSet<>();
    private boolean frozen;

    // here to see how preconditions work properly with parameters
    private static void check(int n) {
        if (n < 0) throw new UnsupportedOperationException();
    }

    @Only(after = "frozen")
    @NotModified
    @NotNull1
    public Stream<String> stream() {
        if (!frozen) throw new UnsupportedOperationException();
        return set.stream();
    }

    @Only(before = "frozen")
    @NotModified
    @NotNull1
    public Stream<String> streamEarly() {
        if (frozen) throw new UnsupportedOperationException();
        return set.stream();
    }

    @Only(before = "frozen")
    @Modified
    public void add(String s) {
        if (frozen) throw new UnsupportedOperationException();
        set.add(s);
    }

    @Mark("frozen")
    @Modified
    public void freeze() {
        if (frozen) throw new UnsupportedOperationException();
        frozen = true;
    }

    @NotModified
    @Only(absent = true)
    public boolean isFrozen() {
        return frozen;
    }
}
