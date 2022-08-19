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

import java.util.HashSet;
import java.util.Set;

@Container
public class Container_3 {

    // third example: independent, so this one works
    // this is not a @Container @Final @NotModified, because strings can be set multiple times, and can be modified

    // important: not linked to p
    @NotModified(absent = true)
    @Final(absent = true)
    @Nullable
    private Set<String> s;

    @Modified
    public void setS(@NotModified @NotNull(content = true) Set<String> p) {
        this.s = new HashSet<>(p);
    }

    public Set<String> getS() {
        return s;
    }

    @Modified
    public void add(String s3) {
        Set<String> set3 = s;
        if (set3 != null) set3.add(s3);
    }
}
