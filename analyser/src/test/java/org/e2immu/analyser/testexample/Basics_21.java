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
Minimal clone of SetOnce; to detect infinite loops when there are self-references.
All is green until the copy() method comes into play.
 */
@E2Container(after = "t")
@Independent1
public class Basics_21<T> {

    @Final(after = "t")
    @Nullable // eventually not-null, not implemented yet
    private volatile T t;

    @Mark("t")
    @Modified
    public void set(@NotNull T t) {
        if (t == null) throw new NullPointerException("Null not allowed");
        synchronized (this) {
            if (this.t != null) {
                throw new IllegalStateException("Already set: have " + this.t + ", try to set " + t);
            }
            this.t = t;
        }
    }

    @Only(after = "t")
    @NotNull
    @NotModified
    public T get() {
        if (t == null) {
            throw new IllegalStateException("Not yet set");
        }
        return t;
    }

    @NotModified
    @TestMark("t")
    public boolean isSet() {
        return t != null;
    }

    @Modified
    @Mark("t")
    public void copy(@NotNull Basics_21<T> other) {
        if (other.isSet()) {
            set(other.get());
        }
    }
}
