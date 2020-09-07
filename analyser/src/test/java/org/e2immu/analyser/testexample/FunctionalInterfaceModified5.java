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

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@Independent
@Container
public class FunctionalInterfaceModified5<T> {

    @SupportData
    private final Set<T> ts;

    @Variable
    private int counter;

    @Modified
    public int incrementAndGet() {
        return ++counter;
    }

    @Independent
    public FunctionalInterfaceModified5(Set<T> ts) {
        this.ts = new HashSet<>(ts);
    }

    // both consumers are allowed to modify T elements;

    // these consumers are NOT allowed to call incrementAndGet
    @NotModified
    public void nonModifyingVisitor(@NotModified(type = AnnotationType.CONTRACT) @Exposed Consumer<T> consumer) {
        for (T t : ts) {
            consumer.accept(t);
        }
    }

    // these consumers are allowed to call incrementAndGet
    @Modified
    public void modifyingVisitor(@Exposed Consumer<T> consumer) {
        for (T t : ts) {
            consumer.accept(t);
        }
    }
}
