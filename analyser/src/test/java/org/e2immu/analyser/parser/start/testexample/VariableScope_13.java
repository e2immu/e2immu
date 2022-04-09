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

/*
tests the "out of scope" scope variable
 */
public class VariableScope_13 {

    interface Y {
    }

    static class X implements Y {
        private int i;

        public void setI(int i) {
            this.i = i;
        }
    }

    public static void method(Y y, String s) {
        int k = y instanceof X x && x.i == s.length() ? x.i : 3;
        System.out.println(k); // x should not exist here!
        int l = y instanceof X x && x.i == s.length() ? x.i : 3;
        System.out.println(l); // x should not exist here!
        assert k == l; // because of increasing statement time, we should have different values for x.i
    }
}
