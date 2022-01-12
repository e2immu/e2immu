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

package org.e2immu.analyser.parser.failing.testexample;

import org.e2immu.annotation.BeforeMark;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Mark;
import org.e2immu.annotation.TestMark;

/*
Testing the immutability properties as statements progress: create an eventually immutable object,
modify it, and cause an error by modifying again.
 */
public class E2InContext_0 {

    @E2Container(after = "t")
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

    @BeforeMark
    public static Eventually<String> notYetSet() {
        return new Eventually<>();
    }

    @E2Container
    public static Eventually<String> alreadySet() {
        Eventually<String> eventually = new Eventually<>();
        eventually.set("hello");
        return eventually;
    }

    @E2Container
    public static Eventually<String> error() {
        Eventually<String> eventually = new Eventually<>();
        eventually.set("hello");
        eventually.set("there");
        return eventually;
    }

}
