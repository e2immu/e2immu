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

package org.e2immu.analyser.parser.eventual.testexample;

import org.e2immu.annotation.*;

/*
copy of EventuallyE1Immutable_0, with an additional setter which modifies input but obviously does
not assign it (it is final).
 */
@E1Immutable(after = "string")
public class EventuallyE1Immutable_3 {

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
    private String string;

    public EventuallyE1Immutable_3(@Modified TwoIntegers input) {
        if (input == null) throw new NullPointerException();
        this.input = input;
    }

    public String getString() {
        return string;
    }

    @Mark("string")
    public void setString(@NotNull String string) {
        if (this.string != null) throw new UnsupportedOperationException();
        if (string == null) throw new NullPointerException();
        this.string = string;
    }

    // variant, with the preconditions switched
    // result should be the same
    @Mark("string")
    public void setString2(@NotNull String string2) {
        if (string2 == null) throw new NullPointerException();
        if (this.string != null) throw new UnsupportedOperationException();
        this.string = string2;
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
