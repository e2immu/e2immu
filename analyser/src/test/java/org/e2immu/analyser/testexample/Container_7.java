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

import org.e2immu.annotation.*;

import java.util.HashMap;
import java.util.Map;

/*
Test to catch a @Container going from 0 to 1 error in intelliJ highlighter
 */
public class Container_7 {

    private static final String XYZ = "xyz";
    private static final String ABC = "abc";
    private static final int oneTwoThree = 123;
    private static final int nineEightSeven = 987;

    // NOTE! this one is not a container, because it is not static
    @E2Immutable
    public class Complex {
        final int i, j;

        public Complex(int i, int j) {
            this.i = i;
            this.j = j;
        }
    }

    final Map<String, Integer> MAP = Map.copyOf(new HashMap<>());

    static <K, V> Map<K, V> put(Map<K, V> map, K k, V v) {
        map.put(k, v);
        return map;
    }

    @ERContainer
    @NotNull
    @NotModified
    public final Map<String, Complex> MAP2 = Map.copyOf(put(put(new HashMap<>(), ABC, new Complex(oneTwoThree, nineEightSeven)),
            XYZ, new Complex(1, 2)));

}


