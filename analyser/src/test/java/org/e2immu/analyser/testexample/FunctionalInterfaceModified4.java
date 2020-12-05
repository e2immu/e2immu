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

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@E2Container
public class FunctionalInterfaceModified4<T> {

    @NotModified
    private final Set<T> ts;

    @Independent
    public FunctionalInterfaceModified4(@NotNull @NotModified Set<T> ts) {
        this.ts = new HashSet<>(ts);
    }

    @NotModified
    public void visit(Consumer<T> consumer) {
        for (T t : ts) {
            consumer.accept(t);
        }
    }

    @NotModified
    public void visit2(@NotNull Consumer<T> consumer) {
        ts.forEach(consumer);
    }

    @NotModified
    public void visit3(@NotNull Consumer<T> consumer) {
        doTheVisiting(consumer, ts);
    }

    @NotModified
    private static <T> void doTheVisiting(@NotNull Consumer<T> consumer, @NotNull @NotModified Set<T> set) {
        set.forEach(consumer);
    }
}
