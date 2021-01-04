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
import org.e2immu.annotation.Final;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

@E2Container
public class E2Immutable_1 {

    @Nullable
    public final E2Immutable_1 parent2;
    @Final
    private int level2;
    @Nullable
    public final String value2;

    public E2Immutable_1(String value) {
        this.parent2 = null;
        level2 = 0;
        this.value2 = value;
    }

    public E2Immutable_1(@NotNull E2Immutable_1 parent2Param, String valueParam2) {
        this.parent2 = parent2Param;
        level2 = parent2Param.level2 + 2;
        this.value2 = valueParam2;
    }

    public int getLevel2() {
        return level2;
    }
}
