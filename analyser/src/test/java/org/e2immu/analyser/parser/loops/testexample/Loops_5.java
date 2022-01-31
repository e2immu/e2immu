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

import org.e2immu.annotation.Constant;

public class Loops_5 {
    // picked up by NumericalRange: standard condition and increment, no extra write to loop variable

    // same as in 4, but with a different variable
    // now we know that i>=10 at the end of the loop, though

    @Constant(absent = true)
    public static int method1() {
        int i = 0;
        for (; i < 10; i++) {
            if (i == 1) return 5;
        }
        assert i >= 10;
        return 0;
    }

    @Constant("1")
    public static int method2() {
        int i = 0;
        for (; i < 10; i++) {
            if (i == 1) break;
        }
        assert i == 1; // always true, but we do not see that; however, after the assert statement,
        return i; // always equal to 1
    }

    // more realistic -- do we know that the exit point has gone?
    @Constant(absent = true)
    public static int method3() {
        int i = 0;
        for (; i < 10; i++) {
            if (i == 1) break;
        }
        assert i == 1 || i == 10; // always true
        return i;
    }
}
