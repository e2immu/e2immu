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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.HashSet;
import java.util.Set;

// example 5 shows the same indirect modification; this time construction is not linked to the set

@FinalFields
@Container
public class Modification_5 {
    @Modified
    private final Set<String> set5;

    public Modification_5(@NotModified Set<String> in5) {
        this.set5 = new HashSet<>(in5);
    }

    @NotModified(absent = true)
    public void add5(String v) {
        Set<String> local5 = set5;
        local5.add(v);
    }
}
