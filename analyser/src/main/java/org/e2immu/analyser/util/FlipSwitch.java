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
public class FlipSwitch {

    @Final(after = "t")
    @Nullable // eventually not-null, not implemented yet
    @Linked(to = {"t"})
    // volatile guarantees that once the value is set, other threads see the effect immediately
    private volatile boolean t;

    @Mark("t")
    @Precondition("null == this.t")
    public void set() { // @NotModified implied
        synchronized (this) {
            if (t) {
                throw new UnsupportedOperationException("Already set");
            }
            t = true;
        }
    }

    @NotModified
    public boolean isSet() {
        return t;
    }

    @Modified
    @Only(before = "t")
    public void copy(FlipSwitch other) {
        if (other.isSet()) set();
    }

    public void copyIfNotSet(FlipSwitch other) {
        if (!isSet() && other.isSet()) set();
    }

    @Override
    public String toString() {
        return "FlipSwitch{" + t + '}';
    }
}
