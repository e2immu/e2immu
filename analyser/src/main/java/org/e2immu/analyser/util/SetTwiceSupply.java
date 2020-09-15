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
 * Variant on SetTwice, where after creation, a <code>Runnable</code>
 * can be provided that is triggered by the <code>get</code> method,
 * and will call the <code>set</code> method in the same thread during its execution.
 * <p>
 * The runnable is cleared in <code>set</code>, to encourage garbage collection.
 * This has no bearing on the immutability.
 *
 * @param <T>
 */
@E2Container(after = "overwritten+t")
public class SetTwiceSupply<T> extends SetTwice<T> {

    @Final(after = "overwritten+t")
    private Runnable runnable;

    @Modified
    @Only(before = "t")
    public void setRunnable(Runnable runnable) {
        if (isSet()) throw new UnsupportedOperationException("Already set");
        this.runnable = runnable;
    }

    @Mark("t")
    @Modified
    public void set(@NotNull T t) {
        super.set(t);
        runnable = null;
    }

    @Only(after = "set")
    @NotNull
    @Modified
    public T getPotentiallyRun() {
        return getPotentiallyRun("?");
    }

    @Only(after = "set")
    @Modified
    @NotNull
    public T getPotentiallyRun(String errorContext) {
        if (!isSet()) {
            if (runnable == null) {
                throw new UnsupportedOperationException("Not yet set, and no runnable provided: " + errorContext);
            }
            // we're assuming the runnable will set the value via the 'set' method
            Runnable localRunnable = runnable;
            runnable = null;
            localRunnable.run();
            if (!isSet())
                throw new NullPointerException("Not yet set, the runnable did not set the value: " + errorContext);
        }
        return get();
    }

    @Modified
    public boolean isSetPotentiallyRun() {
        if (isSet()) return true;
        if (runnable == null) return false;
        Runnable localRunnable = runnable;
        runnable = null;
        localRunnable.run();
        return isSet();
    }

    public boolean hasRunnable() {
        return runnable != null;
    }
}
