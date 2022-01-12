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

import org.e2immu.annotation.Constant;

import java.util.HashSet;
import java.util.Set;

/*
Tests the isKnown() artificial method so that the count in size() goes from 1 to 2
 */
public class BasicCompanionMethods_5 {

    @Constant("true")
    static boolean test() {
        Set<String> set = new HashSet<>();
        set.add("a");
        assert set.contains("a");
        assert set.size() == 1;

        set.add("a");
        assert set.contains("a");
        assert set.size() == 1;

        set.add("b");
        assert set.contains("a");
        assert set.contains("b");
        assert set.size() == 2;

        set.add("b");
        // assert !set.contains("c"); // cannot do that yet, see test 8

        return set.size() == 2;
    }

}
