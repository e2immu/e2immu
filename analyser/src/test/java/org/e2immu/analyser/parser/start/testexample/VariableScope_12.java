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

/*
tests the "out of scope" scope variable
 */
public class VariableScope_12 {

    interface Y {
    }

    static class X implements Y {
        private int i;

        public void setI(int i) {
            this.i = i;
        }
    }

    @Constant(absent = true)
    public static int method(Y y, String s) {
        if (y instanceof X x) {
            if (x.i == s.length()) {
                return x.i;
            }
        }
        return 0; // this one should contain a condition based on "<oos>.i"
    }
}
