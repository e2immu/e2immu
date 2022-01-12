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

package org.e2immu.analyser.parser.loops.testexample;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
for later: interaction of loops with companion methods

while set is not assigned to in the loop, it is modified.
There's currently no provision for that
 */
public class Loops_8_TODO {
    public static void method(List<String> list) {
        Set<String> set = new HashSet<>();
        for (String s : list) {
            set.add(s);
        }
    }
}
