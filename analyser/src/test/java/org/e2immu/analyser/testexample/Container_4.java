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
public class Container_4 {

    @NotNull1
    @Linked(to = {"Container_4:p"})
    private final Set<String> s;

    public Container_4(@NotNull1 Set<String> p) {
        this.s = Objects.requireNonNull(p);
    }

    public Set<String> getS() {
        return s;
    }

    public void m1(@Modified @NotNull Set<String> modified) {
        Set<String> sourceM1 = s;
        modified.addAll(sourceM1);
    }

    public void m2(@Modified @NotNull Set<String> modified2) {
        Set<String> toModifyM2 = modified2;
        toModifyM2.addAll(s);
    }

    // we link the set 'out' to the set 'in', but who cares about this? how can we use this linkage later?
    public static void crossModify(@NotNull1 @NotModified Set<String> in, @NotNull @NotModified(absent = true) Set<String> out) {
        out.addAll(in);
    }

    public boolean contains(String t) {
        return s.contains(t);
    }
}
