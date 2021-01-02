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

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.Set;

@Container(absent = true)
@E1Container(absent = true)
@E1Immutable
public class Container_2 {

    @Linked(to = "Container_2:p")
    @Modified
    @Nullable
    private final Set<String> s;

    @Modified
    public Container_2(@Modified Set<String> p) {
        this.s = p;
    }

    @NotModified
    public Set<String> getS() {
        return s;
    }

    // this method breaks the contract, in a roundabout way
    @Modified
    public void addToS(@NotNull String p2) {
        if (s != null) {
            s.add(p2);
        }
    }
}

