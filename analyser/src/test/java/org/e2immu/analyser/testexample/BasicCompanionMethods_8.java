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

import org.e2immu.annotation.Constant;

import java.util.HashSet;
import java.util.Set;

/*
Tests the $Value$Size companion method on contains and isEmpty
 */
public class BasicCompanionMethods_8 {

    @Constant("1")
    static int test() {
        Set<String> set = new HashSet<>();
        assert !set.contains("b"); // always true   1 - OK

        boolean added1 = set.add("a");
        assert added1; // always true               3 - OK
        assert !set.isEmpty(); // always true       4 OK

        set.clear();
        assert set.isEmpty(); // always true        6 OK

        boolean added2 = set.add("a");
        assert added2;  // always true              8 - OK
        boolean added3 = set.add("a");
        assert !added3;  // always true             10 -OK

        assert set.contains("a"); // always true    11 - OK
        assert !set.contains("b"); // always true   12 OK

        return set.size();
    }

}
