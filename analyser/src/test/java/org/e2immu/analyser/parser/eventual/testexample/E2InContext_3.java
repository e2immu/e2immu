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
import org.e2immu.annotation.eventual.BeforeMark;
import org.e2immu.annotation.eventual.Mark;
import org.e2immu.annotation.eventual.TestMark;

/*
Slight variant on _1, the field is public
 */
public class E2InContext_3 {

    @ImmutableContainer(after = "t", hc = true)
    public static class Eventually<T> {
        private T t;

        @Mark("t")
        public void set(T t) {
            if (t == null) throw new IllegalArgumentException();
            if (this.t != null) throw new IllegalStateException();
            this.t = t;
        }

        @TestMark("t")
        public boolean isSet() {
            return t != null;
        }
    }

    @BeforeMark(absent = true)
    @ImmutableContainer(after = "eventually")
    // @ERContainer because better than @E2Container; the "after=" signifies that it is plainly eventual
    public final Eventually<String> eventually = new Eventually<>();

    // whilst correct the very first time around, the state of eventually can be changed outside this class
    @BeforeMark(absent = true)
    @ImmutableContainer(after = "eventually")
    public Eventually<String> getEventually() {
        return eventually;
    }
}
