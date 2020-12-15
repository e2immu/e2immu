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

import org.e2immu.annotation.E1Container;
import org.e2immu.annotation.NotModified;

import java.util.HashSet;
import java.util.Set;

@E1Container
public class Modification_1 {

    // IMPORTANT: the @NotModified shows that Example2 does not modify it. It can be modified from the outside.
    // this is part of the Level 2 immutability rules.
    @NotModified
    public final Set<String> set2 = new HashSet<>();

    @NotModified
    int size() {
        return set2.size();
    }

    @NotModified
    public String getFirst(String s) {
        return size() > 0 ? set2.stream().findAny().orElseThrow() : "";
    }
}
