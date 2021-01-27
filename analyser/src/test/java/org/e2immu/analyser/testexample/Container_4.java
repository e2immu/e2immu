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

import java.util.Objects;
import java.util.Set;

@Container(absent = true)
public class Container_4 {

    @NotNull1
    @Linked(to = {"Container_4:p"})
    private final Set<String> s;

    public Container_4(@NotNull1 Set<String> p) {
        this.s = Objects.requireNonNull(p);
    }

    public Set<String> getS() {
        return s;
    }

    public void m1(@Modified @NotNull Set<String> modified) {
        Set<String> sourceM1 = s;
        modified.addAll(sourceM1);
    }

    public void m2(@Modified @NotNull Set<String> modified2) {
        Set<String> toModifyM2 = modified2;
        toModifyM2.addAll(s);
    }

    // we link the set 'out' to the set 'in', but who cares about this? how can we use this linkage later?
    public static void crossModify(@NotNull1 @NotModified Set<String> in, @NotNull @NotModified(absent = true) Set<String> out) {
        out.addAll(in);
    }
}
