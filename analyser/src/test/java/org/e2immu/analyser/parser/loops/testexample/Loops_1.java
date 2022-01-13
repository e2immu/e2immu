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
import org.e2immu.annotation.NotNull;

public class Loops_1 {

    @Constant(absent = true)
    @NotNull(absent = true)
    public static String method(int n) {
        String res2 = null; // assignment forced upon us by compiler!
        int i = 0;
        while (true) { // executed at least once, but assignment may not be reachable
            ++i; // as opposed to Loops_0, which has i++
            if (i >= n) break;
            res2 = "abc";
        }
        return res2;
    }

}