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

package org.e2immu.analyser.parser.start.testexample;

import org.e2immu.annotation.*;

import java.util.Set;

@Container(absent = true)
@E1Container(absent = true)
@E1Immutable
public class Container_2 {

    @Linked(to = {"Container_2:p"})
    @Modified
    @Nullable
    private final Set<String> s;

    public Container_2(@Modified Set<String> p) {
        this.s = p;
    }

    @NotModified
    public Set<String> getS() {
        return s;
    }

    // this method breaks the contract, in a roundabout way
    @Modified
    public void addToS(@NotNull String p2) {
        if (s != null) {
            s.add(p2);
        }
    }
}

