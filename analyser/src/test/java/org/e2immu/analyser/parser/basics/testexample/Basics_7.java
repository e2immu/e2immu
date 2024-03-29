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

package org.e2immu.analyser.parser.basics.testexample;

import org.e2immu.annotation.Constant;

/*
Statement time stands still in constructors and synchronization blocks,
where fields are effectively treated as local variables.
 */
public class Basics_7 {

    private int i;

    Basics_7(int p, boolean b) {
        if (b) {
            i = p;
            System.out.println("i is " + i);
        }
        if (i < 10) {
            i = i + 1;
        }
    }

    @Constant("true")
    synchronized boolean increment(int q) {
        int k = i;
        System.out.println("q is " + q);
        i += q;
        System.out.println("i is " + i);
        return i == k + q;
    }

    void increment2() {
        System.out.println("i is " + i);
        synchronized (this) {
            int j = i;
            i++;
            System.out.println("i is " + i);
            assert j == i - 1; // error, always true (in sync block)
        }
    }

    @Constant("true")
    boolean increment3() {
        System.out.println("i is " + i);
        synchronized (this) {
            int j = i;
            i++;
            System.out.println("i is " + i);
            return j == i - 1; // always true
        }
    }
}
