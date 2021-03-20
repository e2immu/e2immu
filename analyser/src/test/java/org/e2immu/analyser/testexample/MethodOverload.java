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

public class MethodOverload {

    @Override
    public int hashCode() {
        return 10;
    }

    interface I1 {
        String method(int i);

        String method(int i, int j);

        String method(int i, String k);
    }

    static class C1 implements I1 {

        @Override
        public String method(int i) {
            return "i=" + i;
        }

        @Override
        public String method(int i, int j) {
            return "i+j=" + (i + j);
        }

        @Override
        public String method(int i, String k) {
            return k + "=" + i;
        }

        @Override
        public String toString() {
            return method(1) + method(1, 2) + method(1, "h");
        }
    }

    static class C2 extends C1 {
        @Override
        public String toString() {
            return "C2 goes to C1:" + super.toString();
        }

        @Override
        public String method(int i, int j) {
            return "C2 sum=" + (i + j);
        }
    }
}
