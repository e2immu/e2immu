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

public class FieldResolution_0 {

    static class C1 {
        public final String s1;

        public C1(String in1, C2 c2) {
            s1 = in1 + c2.prefix2;
        }
    }

    static class C2 {
        public final String prefix2;

        public C2(String in2) {
            prefix2 = in2;
        }

        public String withC1(C1 c1) {
            return c1.s1 + prefix2;
        }
    }
}
