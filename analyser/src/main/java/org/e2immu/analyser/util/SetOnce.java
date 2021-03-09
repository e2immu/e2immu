/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.util;

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
                throw new UnsupportedOperationException("Already set: have " + this.t + ", try to set " + t);
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
            throw new UnsupportedOperationException("Not yet set");
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
            throw new UnsupportedOperationException("Not yet set: " + message);
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
