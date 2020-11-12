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

import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.Size;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class SizeCopy {

    private final Set<String> f1 = new HashSet<>();

    public SizeCopy(@Size(copy = true) Set<String> p0) {
        f1.addAll(p0);
    }

    @Size(copy = true)
    @NotModified
    public Stream<String> getStream() {
        return f1.stream();
    }

    // most importantly in this test, we want @Size(copy = true) to be absent
    @Size(equals = 1)
    @NotModified
    public Stream<String> getStream2() {
        if (f1.isEmpty()) return Stream.of("a");
        return f1.stream().limit(1);
    }
}
