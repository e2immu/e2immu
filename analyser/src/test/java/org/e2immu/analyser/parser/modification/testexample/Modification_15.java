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

package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

/*
Compare this one to Modification_14.
Example shows that direct assignment into sub-type, even if it causes a warning,
should also count for a modification ?

Decision 20200209 for now, we upgrade the warning to an error, and keep @NotModified

 */
public class Modification_15 {

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
    @NotModified
    public final TwoIntegers input;

    public Modification_15(@NotModified TwoIntegers input) {
        if (input == null) throw new NullPointerException();
        this.input = input;
    }

    @NotModified
    public int getI() {
        return input.i;
    }

    @Modified
    public void setI(int i) {
        input.i = i;
    }
}
