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

import java.util.HashSet;
import java.util.Set;

@Container
public class Container_3 {

    // third example: independent, so this one works
    // this is not a @Container @Final @NotModified, because strings can be set multiple times, and can be modified

    // important: not linked to p
    @Linked(absent = true)
    @NotModified(absent = true)
    @Variable
    @Nullable
    private Set<String> s;

    @Modified
    public void setS(@NotModified @NotNull1 Set<String> p) {
        this.s = new HashSet<>(p);
    }

    public Set<String> getS() {
        return s;
    }

    @Modified
    public void add(String s3) {
        Set<String> set3 = s;
        if (set3 != null) set3.add(s3);
    }
}
