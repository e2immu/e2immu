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
import org.e2immu.annotation.Exposed;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotModified1;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static org.e2immu.annotation.AnnotationType.CONTRACT;

@E2Container
public class FunctionalInterfaceModified4<T> {

    private final Set<T> ts;

    public FunctionalInterfaceModified4(Set<T> ts) {
        this.ts = new HashSet<>(ts);
    }

    public void visit(@Exposed Consumer<T> consumer) {
        for (T t : ts) {
            consumer.accept(t);
        }
    }

}
