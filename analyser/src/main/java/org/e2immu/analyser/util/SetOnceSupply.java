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

@E1Container(after = "set", type = AnnotationType.CONTRACT)
public class SetOnceSupply<T> {
    // volatile guarantees that once the value is set, other threads see the effect immediately
    private volatile T t;
    private Runnable runnable;

    public void setRunnable(Runnable runnable) {
        this.runnable = runnable;
    }

    @Mark("set")
    @Only(before = "set")
    public void set(@NotNull T t) { // @NotModified implied
        if (t == null) throw new NullPointerException("Null not allowed");
        synchronized (this) {
            if (this.t != null) {
                throw new UnsupportedOperationException("Already set");
            }
            this.t = t;
            runnable = null;
        }
    }

    @Only(after = "set")
    public T get() {
        return get("?");
    }

    @Only(after = "set")
    public T get(@NotNull String errorContext) {
        if (t == null) {
            if (runnable == null) {
                throw new UnsupportedOperationException("Not yet set, and no runnable provided: " + errorContext);
            }
            // we're assuming the runnable will set the value via the 'set' method
            Runnable localRunnable = runnable;
            runnable = null;
            localRunnable.run();
            if (t == null)
                throw new NullPointerException("Not yet set, the runnable did not set the value: " + errorContext);
        }
        return t;
    }

    // TODO need special semantics for this one...
    public void overwrite(@NotNull T t) {
        if (t == null) throw new NullPointerException("Null not allowed");
        synchronized (this) {
            if (this.t == null) {
                throw new UnsupportedOperationException("Not yet set; do not use overwrite lightly");
            }
            this.t = t;
        }
    }

    @NotModified
    public boolean isSetDoNotTriggerRunnable() {
        return t != null;
    }

    @NotModified
    public boolean isSet() {
        if (t != null) return true;
        if (runnable == null) return false;
        Runnable localRunnable = runnable;
        runnable = null;
        localRunnable.run();
        return t != null;
    }

    public boolean hasRunnable() {
        return runnable != null;
    }
}
