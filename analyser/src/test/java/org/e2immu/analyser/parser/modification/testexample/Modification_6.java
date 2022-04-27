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

import java.util.Set;

// example 6 is direct modification, but indirectly on an instance variable of the class

@E1Immutable
@Container(absent = true)
public class Modification_6 {

    @Modified
    @NotNull
    private final Set<String> set6;

    public Modification_6(@Modified @NotNull Set<String> in6) {
        this.set6 = in6;
    }

    @NotModified
    public static void add6(@NotNull @Modified Modification_6 example6, @NotNull1 @NotModified Set<String> values6) {
        example6.set6.addAll(values6);
    }

}
