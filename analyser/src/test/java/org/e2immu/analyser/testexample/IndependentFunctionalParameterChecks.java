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

import org.e2immu.annotation.E1Container;
import org.e2immu.annotation.SupportData;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Goal is to verify independence rules for functional parameters
 */
public class IndependentFunctionalParameterChecks {

    @E1Container
    static class Example1<T> {
        @SupportData
        private final Set<T> data;

        public Example1(Set<T> ts) {
            this.data = new HashSet<>(ts);
        }

        public T getFirst() {
            return stream().findFirst().orElseThrow();
        }

        public Stream<T> stream() {
            return data.stream();
        }

        public void unsafeVisit(Consumer<Set<T>> consumer) {
            consumer.accept(data);
        }

        public void safeVisit(Consumer<T> consumer) {
            data.forEach(consumer);
        }
    }
}
