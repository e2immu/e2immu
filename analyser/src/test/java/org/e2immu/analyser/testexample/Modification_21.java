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

import org.e2immu.annotation.Modified;

import java.util.HashSet;
import java.util.Set;

/*
Infinite loop is broken, but too harshly.
There is no link between c and s2, because Set<String> is implicitly immutable in C1 (TODO, not quite correct!)
 */
public class Modification_21 {

    final Set<String> s2 = new HashSet<>();

    // implicitly immutable
    static class C1 {
        @Modified
        final Set<String> set;

        C1(@Modified Set<String> setC) {
            this.set = setC;
        }
    }

    private boolean example1() {
        C1 c = new C1(s2);
        C1 localD = new C1(Set.of("a", "b", "c"));
        return addAll(c.set, localD.set);
    }

    private static boolean addAll(Set<String> c, Set<String> d) {
        return c.addAll(d);
    }
}
