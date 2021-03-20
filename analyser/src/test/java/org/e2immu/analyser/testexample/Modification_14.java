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

/*
Compare this one to Modification_15.
Example shows that direct assignment into sub-type, even if it causes a warning,
should also count for a modification ?
 */

@E1Immutable // but not a container!
public class Modification_14 {

    @Container
    public static class TwoIntegers {
        private int i;
        private int j;

        public int getI() {
            return i;
        }

        public int getJ() {
            return j;
        }

        public void setI(int i) {
            this.i = i;
        }

        public void setJ(int j) {
            this.j = j;
        }
    }

    @NotNull
    @Modified
    public final TwoIntegers input;

    public Modification_14(@Modified TwoIntegers input) {
        if (input == null) throw new NullPointerException();
        this.input = input;
    }

    @NotModified
    public int getI() {
        return input.i;
    }

    @Modified
    public void setI(int i) {
        input.setI(i);
    }
}
