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

public class Basics_8 {

    // the first test isn't that interesting, in that v always gets substituted for a value

    @Constant("true")
    static boolean test1(int l) {
        int v = l;
        int w = v + 1; // == l+1
        assert w == l + 1; // always true
        v = v + 1; // == l+1
        int u = v + 2; // == l+3
        assert u == l + 3; // always true
        v = v + 1; // == l+2
        return v == u - 1;
    }

    private int i;

    // statement time doesn't advance because of the synchronization

    @Constant("true")
    synchronized boolean test2(int q) {
        int j = i; // some value
        int i2 = i + q; // some value + q
        int k = i2;
        return k == j + q;
    }

    @Constant("true")
    synchronized boolean test3(int q) {
        int j = i; // some value
        i = i + q; // some value + q
        final int k = i;
        return k == j + q;
    }

    // more complicated, to verify that the correct statement and statement times are used
    void test4(int q) {
        System.out.println("i is " + i); // time 1
        int j = i;
        System.out.println("i is again " + i); // time 2
        int k = i;
        if (j == k) {
            synchronized (this) {
                int j0 = i; // some value
                i = i + q; // some value + q
                int k0 = i;
                assert k0 == j0 + q; // always true
            }
        }
    }
}
