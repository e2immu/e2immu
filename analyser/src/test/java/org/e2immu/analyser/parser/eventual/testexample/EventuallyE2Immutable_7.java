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

package org.e2immu.analyser.parser.eventual.testexample;

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Mark;
import org.e2immu.annotation.Only;
import org.e2immu.annotation.TestMark;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@E2Container(after = "set")
public class EventuallyE2Immutable_7<T> {

    private final Set<T> set = new HashSet<>();

    @Mark("set")
    public void initialize(Set<T> data) {
        if (set.size() > 0) throw new IllegalStateException();
        if (data.size() <= 0) throw new IllegalArgumentException();
        set.addAll(data);
    }

    @Only(after = "set")
    public Stream<T> stream() {
        if (set.size() <= 0) throw new IllegalStateException();
        return set.stream();
    }

    public int size() {
        return set.size();
    }

    @TestMark("set")
    public boolean hasBeenInitialised() {
        return set.size() > 0;
    }
}

