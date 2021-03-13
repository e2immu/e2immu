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

import org.e2immu.annotation.NotNull;

import java.util.Objects;

public class FirstThen_0<S> {

    private S first;

    public FirstThen_0(@NotNull S first) {
        this.first = Objects.requireNonNull(first);
    }

    /* code goes in infinite loop when the if(...) statement is present
     */
    public void set() {
        if (first == null) throw new UnsupportedOperationException("Already set");
        first = null;
    }
}