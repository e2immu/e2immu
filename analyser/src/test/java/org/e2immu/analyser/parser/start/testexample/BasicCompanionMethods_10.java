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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/*
Tests the $Clear on other methods... we need to know when to stop
 */
public class BasicCompanionMethods_10 {

    @ImmutableContainer // but not constant
    static int test(Collection<String> in) {
        Set<String> set = new HashSet<>();
        boolean added = set.add("a");
        assert added;
        assert set.contains("a"); // true
        assert !set.contains("b");// true

        set.addAll(in);

        assert set.contains("a"); // unknown, that's a shame
        assert !set.contains("b");// unknown, that's OK

        return set.size(); // not a constant
    }

}
