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

package org.e2immu.support;

import org.e2immu.annotation.*;

import java.util.Objects;

@E2Container(after = "t")
public class SetOnce<T> {

    @Final(after = "t")
    @Nullable // eventually not-null, not implemented yet
    @Linked(absent = true)
    // volatile guarantees that once the value is set, other threads see the effect immediately
    private volatile T t;

    private boolean set$Precondition(T t) {
        return this.t == null;
    }

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

    private boolean get$Precondition() {
        return t != null;
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

    private boolean get$Precondition(String message) {
        return t != null;
    }

    @Only(after = "t")
    @NotNull
    @NotModified
    public T get(String message) {
        if (t == null) {
            throw new IllegalStateException("Not yet set: " + message);
        }
        return t;
    }

    @NotModified
    @TestMark("t")
    public boolean isSet() {
        return t != null;
    }

    @NotModified
    public T getOrElse(T alternative) {
        if (isSet()) return get();
        return alternative;
    }

    @Modified
    @Mark("t") // conditionality left out at the moment
    public void copy(SetOnce<T> other) {
        if (other.isSet()) set(other.get());
    }

    @Override
    public String toString() {
        return "SetOnce{" + "t=" + t + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SetOnce<?> setOnce = (SetOnce<?>) o;
        return Objects.equals(t, setOnce.t);
    }

    @Override
    public int hashCode() {
        return Objects.hash(t);
    }
}
