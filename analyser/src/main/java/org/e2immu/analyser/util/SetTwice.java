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

/**
 * Semantics: set once with `set`, then either overwrite with `overwrite`, or freeze with `freeze`.
 * Setting with <code>set</code> changes <code>t</code> from null to not-null;
 * overwriting or freezing changes <code>overwritten</code> to true.
 * <p>
 * From the point of view of eventual immutability, there are two allowed preconditions.
 * In before state, they are:
 * <p>
 * First: null == this.t
 * Second: not (null == this.t) and not overwritten
 *
 * @param <T>
 */
@E2Container(after = "overwritten+t, t")
public class SetTwice<T> {

    @Final(after = "overwritten+t")
    private volatile boolean overwritten;

    @Final(after = "t")
    @Nullable // eventually not-null, not implemented yet
    @Linked(to = {"t"})
    // volatile guarantees that once the value is set, other threads see the effect immediately
    private volatile T t;

    @Mark("overwritten,t")
    @Modified
    @Precondition("(not (this.overwritten) and not (null == this.t))")
    public void overwrite(@NotNull T t) {
        if (t == null) throw new NullPointerException("Null not allowed");
        synchronized (this) {
            if (!isSet() || overwritten) {
                throw new UnsupportedOperationException("Not yet set");
            }
            this.t = t;
        }
    }

    @Mark("overwritten,t")
    @Modified
    @Precondition("(not (this.overwritten) and not (null == this.t))")
    public void freeze() {
        if (!isSet() || overwritten) {
            throw new UnsupportedOperationException("Not yet set");
        }
        overwritten = true;
    }

    @Mark("t")
    @Modified
    @Precondition("null == this.t")
    public void set(@NotNull T t) {
        if (t == null) throw new NullPointerException("Null not allowed");
        synchronized (this) {
            if (isSet()) {
                throw new UnsupportedOperationException("Already set");
            }
            this.t = t;
        }
    }

    @Only(after = "t")
    @NotNull
    @NotModified
    @Precondition("not (null == this.t)")
    public T get() {
        if (!isSet()) {
            throw new UnsupportedOperationException("Not yet set");
        }
        return t;
    }

    @NotModified
    public final boolean isSet() {
        return t != null;
    }
}
