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

// example 6 is direct modification, but indirectly on an instance variable of the class

@E1Immutable
@Container(absent = true)
public class Modification_6 {

    @Modified
    @NotNull
    private final Set<String> set6;

    public Modification_6(@Modified @NotNull Set<String> in6) {
        this.set6 = in6;
    }

    @Modified
    public static void add6(@NotNull @Modified Modification_6 example6, @NotNull1 @NotModified Set<String> values6) {
        example6.set6.addAll(values6);
    }

}
