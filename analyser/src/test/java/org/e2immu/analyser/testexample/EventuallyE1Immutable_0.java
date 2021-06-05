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

@E1Container(after = "string")
public class EventuallyE1Immutable_0 {
    private static final String STRING = "string";

    /* the presence of a field of the TwoIntegers type ensures that EventuallyE1Immutable_0 is not
    level 2 immutable. The type is not implicitly immutable because of the access in getI
     */
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
    @Final(after = STRING)
    private String string;

    public EventuallyE1Immutable_0(@NotModified TwoIntegers input) {
        if (input == null) throw new NullPointerException();
        this.input = input;
    }

    public String getString() {
        return string;
    }

    /*
    this order of testing this.string and string currently causes a delay on @NotNull
     */
    @Mark(STRING)
    public void setString(@NotNull String string) {
        if (this.string != null) throw new UnsupportedOperationException();
        if (string == null) throw new NullPointerException();
        this.string = string;
    }

    /* variant, with the preconditions switched. Result should be the same, but is necessary to test.
     */
    @Mark(STRING)
    public void setString2(@NotNull String string2) {
        if (string2 == null) throw new NullPointerException();
        if (this.string != null) throw new UnsupportedOperationException();
        this.string = string2;
    }

    @NotModified
    public int getI() {
        return input.i;
    }
}
