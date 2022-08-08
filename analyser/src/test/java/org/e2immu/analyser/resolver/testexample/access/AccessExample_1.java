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

package org.e2immu.analyser.resolver.testexample.access;

public class AccessExample_1 {

    private static class I1 {

        public static final int I = 42; // effective visibility = private
        private static final int J = 420; // effective visibility = private

        public static class I2 {
            public static final int IK = 43; // effective visibility = private
            private static final int JK = 430; // effective visibility = private
        }
    }


    public static class I3 {
        static int L = 32; // effective visibility = package-private
        protected static int M = 33; // effective visibility = protected (members of same package only, because static)

        protected int N = 3; // effective visibility = protected (sub-classes and members of same package)

        @Override
        public String toString() { // must be public, overrides public method
            return "i=" + I1.I + ", j=" + I1.J + ", ik=" + I1.I2.IK + ", jk=" + I1.I2.JK; // all in the same primary type
        }
    }
}
