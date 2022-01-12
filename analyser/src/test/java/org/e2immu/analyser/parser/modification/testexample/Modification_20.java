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

import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

/*
Breaking an infinite delay loop; see explanation in Test_16_Modification_2.

Set<String> is now transparent in C1; C1 is not transparent in Modification_20.
addAll is non-modifying as compared to modifying in _19
 */
public class Modification_20 {

    final Set<String> s2 = new HashSet<>();

    static class C1 {
        @Nullable
        final Set<String> set;

        C1(Set<String> setC) {
            this.set = setC;
        }
    }

    private boolean example1() {
        C1 c = new C1(s2);
        C1 localD = new C1(Set.of("a", "b", "c"));
        return addAll(c.set, localD.set);
    }

    @NotModified
    private static boolean addAll(Set<String> c, Set<String> d) {
        return c.size() == d.size();
    }
}
