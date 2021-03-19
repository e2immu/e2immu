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

@E2Immutable
public class E2Immutable_3 {

    @E2Container
    @NotNull1
    public final Set<String> strings4;

    @Independent
    public E2Immutable_3(@NotNull1 @NotModified Set<String> input4) {
        strings4 = Set.copyOf(input4);
    }

    @E2Container
    @NotNull1
    @Constant(absent = true)
    public Set<String> getStrings4() {
        return strings4;
    }

    @Identity
    @Linked(absent = true)
    @Constant(absent = true)
    @NotNull
    public Set<String> mingle(@NotNull @Modified Set<String> input4) {
        input4.addAll(strings4);
        return input4;
    }
}
