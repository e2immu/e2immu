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

@ERContainer
public class E2Immutable_2 {

    @Linked(absent = true)
    @Linked1(absent = true)
    @NotModified
    private final Set<String> set3;

    public E2Immutable_2(@Independent Set<String> set3Param) {
        set3 = new HashSet<>(set3Param); // not linked
    }

    @NotNull1
    @ERContainer
    public Set<String> copy() {
        return Set.copyOf(set3);
    }
}
