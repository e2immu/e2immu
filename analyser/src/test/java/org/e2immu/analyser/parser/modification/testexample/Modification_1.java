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

import org.e2immu.annotation.E1Container;
import org.e2immu.annotation.NotModified;

import java.util.HashSet;
import java.util.Set;

@E1Container
public class Modification_1 {

    // IMPORTANT: the @NotModified shows that Modification_1 does not modify it. It can be modified from the outside.
    // this is part of the Level 2 immutability rules.
    @NotModified
    public final Set<String> set2 = new HashSet<>();

    @NotModified
    int size() {
        return set2.size();
    }

    @NotModified
    public String getFirst(String s) {
        return size() > 0 ? set2.stream().findAny().orElseThrow() : "";
    }
}
