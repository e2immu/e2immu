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

@E2Container
public class FunctionalInterfaceModified4<T> {

    @NotModified
    private final Set<T> ts;

    @Independent
    public FunctionalInterfaceModified4(Set<T> ts) {
        this.ts = new HashSet<>(ts);
    }

    /*
     The reasoning behind visit being @NotModified, with an @Exposed consumer:

     1. Unless specified with @NotModified1 on consumer, accept modifies its parameter.
     2. The enclosing type has no means to modify T, as it is an unbound generic type.
     3. Via ObjectFlows the analyser knows that t is part of the method's fields' object graph

     4. Combining the above leads us to the the path of exposure: the method is @NotModified, and the consumer
        is marked @Exposed
     */

    @NotModified //no self-modifications are possible (no other modifying methods)
    public void visit(Consumer<T> consumer) {
        for (T t : ts) {
            consumer.accept(t);
        }
    }

    @NotModified // forEach is @NotModified
    public void visit2(Consumer<T> consumer) {
        ts.forEach(consumer);
    }

    @NotModified // simply following modification rules
    public void visit3(Consumer<T> consumer) {
        doTheVisiting(consumer, ts);
    }

    @NotModified // trivially, not touching fields
    private static <T> void doTheVisiting(Consumer<T> consumer, @NotModified Set<T> set) {
        set.forEach(consumer);
    }
}
