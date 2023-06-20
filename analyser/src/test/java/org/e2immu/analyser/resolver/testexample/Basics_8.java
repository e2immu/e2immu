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

package org.e2immu.analyser.resolver.testexample;

// orphan to type
// comment on type
public class Basics_8 {

    // orphan to field 1
    // comment on field 1
    public static final int CONSTANT_1 = 3;

    // orphan to method
    // comment on method
    public static int method(int in) {
        // orphan on if
        // comment on 'if'
        if (in > 9) {
            return 1;
        }
        System.out.println("in = " + in);
        return in;
    }

    // orphan to field 2
    // comment on field 2
    public static final int CONSTANT_2 = 3;
}
