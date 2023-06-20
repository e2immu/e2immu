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

import org.e2immu.annotation.*;
import org.e2immu.annotation.eventual.Mark;
import org.e2immu.annotation.eventual.Only;

import java.util.HashSet;
import java.util.Set;

/* direct COPY-PASTE into the manual, @E1Container annotation */

@FinalFields(after = "j")
@Container
class EventuallyE1Immutable_2_M {

    @NotModified(after = "j")
    private final Set<Integer> integers = new HashSet<>();

    @Final(after = "j")
    private int j;

    @Modified
    @Only(after = "j")
    public boolean addIfGreater(int i) {
        if (this.j <= 0) throw new UnsupportedOperationException("Not yet set");
        if (i >= this.j) {
            integers.add(i);
            return true;
        }
        return false;
    }

    @NotModified
    public Set<Integer> getIntegers() {
        return integers;
    }

    @NotModified
    public int getJ() {
        return j;
    }

    @Modified
    @Mark("j")
    public void setPositiveJ(int j) {
        if (j <= 0) throw new UnsupportedOperationException();
        if (this.j > 0) throw new UnsupportedOperationException("Already set");

        this.j = j;
    }

    @Modified
    @Only(before = "j")
    public void setNegativeJ(int j) {
        if (j > 0) throw new UnsupportedOperationException();
        if (this.j > 0) throw new UnsupportedOperationException("Already set");
        this.j = j;
    }
}