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

import java.util.HashSet;
import java.util.Set;

/*
simplest modification of Modification_11 that causes delay problems.

1: In example 1, CM van 's2' only when we knew CM of parameter 'setC'
2: In C1(), 'setC' is modified only when we know if 'set' is modified
3: In C1, set is modified only when we know that the links have been established in statement 2 of example1,
   because set is read in that method
4: Links have not been established in 2, 1, 0, because we do not know the modification status of 's2'

This is an infinite delay.

TODO at the moment, C
 */
public class Modification_20 {

    final Set<String> s2 = new HashSet<>();

    static class C1 {
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

    // non-modifying as compared to modifying in _19
    private static boolean addAll(Set<String> c, Set<String> d) {
        return c.size() == d.size();
    }
}
