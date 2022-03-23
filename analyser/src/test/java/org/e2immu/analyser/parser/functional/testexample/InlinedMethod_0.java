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

package org.e2immu.analyser.parser.functional.testexample;

import org.e2immu.annotation.Constant;

public class InlinedMethod_0 {

    private static int product(int x, int y) {
        return x*y;
    }
    private static int square(int x) {
        return product(x, x);
    }

    private static int withIntermediateVariables(int x, int y) {
        int sum = x+y;
        int diff = x-y;
        return sum * diff;
    }

    @Constant("6")
    public static final int m1 = product(2, 3);
    @Constant("16")
    public static final int m2 = square(4);

    @Constant("-24")
    public static final int m3 = withIntermediateVariables(5, 7);
}
