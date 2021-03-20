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

public class Loops_4 {

    /*
    Depending on increasing fidelity, the analyser first produces

    - constant 0 (as there is no "state" after the for-loop)
    - then some mix between 0 and 4 (not realising that the if is not conditional given the loop)
    - finally constant 4 (when realising that the inner if will eventually be true)

     */
    @Constant(absent = true)
    public static int method() {
        for (int i = 0; i < 10; i++) {
            if (i == 1) return 4;
        }
        return 0;
    }

}
