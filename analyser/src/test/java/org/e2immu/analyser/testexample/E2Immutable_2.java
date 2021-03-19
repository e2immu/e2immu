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

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Linked;
import org.e2immu.annotation.NotModified;

import java.util.HashSet;
import java.util.Set;

@E2Container
public class E2Immutable_2 {

    // 1. all fields are final
    // 2. all fields are not modified
    // 3. fields are private
    // 4. constructor and method are independent

    @Linked(absent = true)
    @NotModified
    private final Set<String> set3;

    @Independent
    public E2Immutable_2(Set<String> set3Param) {
        set3 = new HashSet<>(set3Param); // not linked
    }

    @E2Container
    public Set<String> getSet3() {
        return Set.copyOf(set3);
    }
}
