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

@E2Container(after = "t")
public class SetOnce<T> {

    @Final(after = "t")
    @Nullable // eventually not-null, not implemented yet
    @Linked(to = {"t"})
    // volatile guarantees that once the value is set, other threads see the effect immediately
    private volatile T t;

    @Mark("t")
    @Precondition("null == this.t")
    public void set(@NotNull T t) { // @NotModified implied
        if (t == null) throw new NullPointerException("Null not allowed");
        synchronized (this) {
            if (this.t != null) {
                throw new UnsupportedOperationException("Already set: have " + this.t + ", try to set " + t);
            }
            this.t = t;
        }
    }

    @Only(after = "t")
    @NotNull
    @NotModified
    @Independent(absent = true) // note: independent of the support data, which is not present!
    @Precondition("not (null == this.t)")
    public T get() {
        if (t == null) {
            throw new UnsupportedOperationException("Not yet set");
        }
        return t;
    }

    @Only(after = "t")
    @NotNull
    @NotModified
    @Independent(absent = true) // note: independent of the support data, which is not present!
    @Precondition("not (null == this.t)")
    public T get(String message) {
        if (t == null) {
            throw new UnsupportedOperationException("Not yet set: " + message);
        }
        return t;
    }

    @NotModified
    public boolean isSet() {
        return t != null;
    }

    public T getOrElse(T alternative) {
        if (isSet()) return get();
        return alternative;
    }

    @Modified
    @Only(before = "t")
    public void copy(SetOnce<T> other) {
        if (other.isSet()) set(other.get());
    }

    public void copyIfNotSet(SetOnce<T> other) {
        if (!isSet() && other.isSet()) set(other.get());
    }

    @Override
    public String toString() {
        return "SetOnce{" +
                "t=" + t +
                '}';
    }
}
