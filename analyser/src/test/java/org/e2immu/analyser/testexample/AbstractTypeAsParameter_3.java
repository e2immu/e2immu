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
import java.util.stream.Stream;

/*
Situation: consumer applied to a non-implicitly immutable field.

This type is not @E2Immutable because doSomething is not @Independent.
The consumer remains @NotModified, because it is not applied to a parameter.
 */

@E1Container
public class AbstractTypeAsParameter_3 {

    private final Set<Integer> integers;

    @Independent
    public AbstractTypeAsParameter_3(@NotModified Set<Integer> set) {
        integers = new HashSet<>(set);
    }

    @NotModified
    public void doSomething(@NotModified @Dependent Consumer<Set<Integer>> consumer) {
        consumer.accept(integers);
    }

    public static void enrichWith27(@Modified AbstractTypeAsParameter_3 in) {
        in.doSomething(set -> set.add(27)); // modifying lambda modifies in
    }

    public static void print(@NotModified AbstractTypeAsParameter_3 in) {
        in.doSomething(System.out::println); // non-modifying method reference -> in not modified
    }
}
