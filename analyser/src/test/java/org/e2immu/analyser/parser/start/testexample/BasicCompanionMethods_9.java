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


import org.e2immu.annotation.ImmutableContainer;

import java.util.HashSet;
import java.util.Set;

/*
Tests the $Remove companion method
 */
public class BasicCompanionMethods_9 {

    @ImmutableContainer("1")
    static int test() {
        Set<String> set = new HashSet<>();
        boolean added = set.add("a");
        assert added;                 // 2 OK
        assert set.contains("a");     // 3 OK

        boolean removed1 = set.remove("a");
        assert removed1;              // 5 OK
        assert !set.contains("a");    // 6 OK
        assert set.size() == 0;       // 7 OK
        assert set.isEmpty();         // 8 OK

        set.add("c");

        assert !set.contains("b");    // 10 OK
        boolean removed2 = set.remove("b");
        assert !removed2;             // 12 OK

        return set.size();
    }

}
