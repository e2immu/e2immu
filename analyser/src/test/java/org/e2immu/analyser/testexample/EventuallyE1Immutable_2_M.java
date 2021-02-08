/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.HashSet;
import java.util.Set;

/* direct COPY-PASTE into the manual, @E1Container annotation */

@E1Container(after = "j")
class EventuallyE1Immutable_2_M {

    @Modified
    private final Set<Integer> integers = new HashSet<>();

    @Final(after = "j")
    private int j;

    @Modified
    @Only(after = "j")
    public boolean addIfGreater(int i) {
        if (this.j <= 0) throw new UnsupportedOperationException("Not yet set");
        if (i >= this.j) {
            integers.add(i);
            return true;
        }
        return false;
    }

    @NotModified
    public Set<Integer> getIntegers() {
        return integers;
    }

    @NotModified
    public int getJ() {
        return j;
    }

    @Modified
    @Mark("j")
    public void setPositiveJ(int j) {
        if (j <= 0) throw new UnsupportedOperationException();
        if (this.j > 0) throw new UnsupportedOperationException("Already set");

        this.j = j;
    }

    @Modified
    @Only(before = "j")
    public void setNegativeJ(int j) {
        if (j > 0) throw new UnsupportedOperationException();
        if (this.j > 0) throw new UnsupportedOperationException("Already set");
        this.j = j;
    }
}