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

import java.util.function.Consumer;

import static org.e2immu.annotation.AnnotationType.CONTRACT;

public class FunctionalInterfaceModified3<T> {

    private final T t1;

    private final T t2;

    public FunctionalInterfaceModified3(T t1, T t2) {
        this.t1 = t1;
        this.t2 = t2;
    }

    @NotModified
    public void acceptT1(@Exposed Consumer<T> consumer) {
        consumer.accept(t1);
    }

    // exposure, but in a way that is guaranteed to be safe
    @NotModified
    public void acceptT2(@NotModified1(type = CONTRACT) Consumer<T> consumer) {
        consumer.accept(t2);
    }

}
