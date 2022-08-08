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

import org.e2immu.analyser.resolver.testexample.access.AccessExample_1;

public class Access_1 {

    public static void main(String[] args) {
        //AccessExample_1.I1.I2 unreachable
        //AccessExample_1.I1.I unreachable
        //AccessExample_1.I3.L unreachable
        //AccessExample_1.I3.M unreachable
        AccessExample_1.I3 ac = new AccessExample_1.I3();
        System.out.println("Access example is: " + ac);
    }

    static class Sub extends AccessExample_1.I3 {
        @Override
        public String toString() {
            // not accessible: AccessExample_1.I3.L;
            return "n = " + N; //!! but this one is
        }
    }

}
