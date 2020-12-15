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

@E1Immutable
@Container(absent = true)
public class Modification_4 {

    @Modified
    @NotNull
    private final Set<String> set4;

    public Modification_4(@NotModified(absent = true) @NotNull Set<String> in4) {
        this.set4 = in4;
    }

    @Modified
    public void add4(@NotNull String v) {
        Set<String> local4 = set4;
        local4.add(v); // this statement induces a @NotNull on in4
    }
}
