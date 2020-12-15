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

import java.util.Set;
import java.util.stream.Stream;

/**
 * Basic example of modification travelling from the method <code>add</code>
 * to the field <code>set</code>, then to the parameter <code>input</code>.
 * <p>
 * At the same time, the not-null property travels along.
 */
@E1Immutable
public class Modification_7 {

    @NotNull
    @Modified
    @Final
    private Set<String> set;

    public Modification_7(@NotNull @Modified Set<String> input) {
        set = input;
    }

    @NotModified
    public Stream<String> stream() {
        return set.stream();
    }

    @NotModified
    public Set<String> getSet() {
        return set;
    }

    @Modified
    public void add(String s) {
        set.add(s);
    }
}
