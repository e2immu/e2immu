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

@Immutable
public class E2Immutable_3 {

    @ImmutableContainer
    @NotNull(content = true)
    public final Set<String> strings4;

    public E2Immutable_3(@NotNull(content = true) @NotModified Set<String> input4) {
        strings4 = Set.copyOf(input4);
    }

    @ImmutableContainer // but not constant
    @NotNull(content = true)
    public Set<String> getStrings4() {
        return strings4;
    }

    @Identity
    @NotNull
    @Independent // not dependent on strings4!
    public Set<String> mingle(@NotNull @Modified @Independent Set<String> input4) {
        input4.addAll(strings4);
        return input4;
    }
}
