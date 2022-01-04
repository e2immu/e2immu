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

import org.e2immu.annotation.*;

// variant on DependentVariables_1; now X is not transparent anymore in XS
// XS is an E1Container now
public class DependentVariables_2 {

    @Container
    @Independent
    static class X {
        private int i;

        public void setI(int i) {
            this.i = i;
        }

        public int getI() {
            return i;
        }
    }

    @E1Container
    static class XS {
        @E1Container
        @Linked1(to = {"XS:xs"})
        @NotModified
        @NotNull
        private final X[] xs;

        public XS(X[] xs) {
            this.xs = new X[xs.length];
            System.arraycopy(xs, 0, this.xs, 0, xs.length);
        }

        public X getX(int index) {
            return xs[index];
        }

        public int getI(int index) {
            return xs[index].i;
        }
    }
}
