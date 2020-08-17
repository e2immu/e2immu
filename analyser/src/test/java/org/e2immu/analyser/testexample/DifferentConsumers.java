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

import org.e2immu.annotation.AnnotationType;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotModified1;

import java.util.Collection;
import java.util.function.Consumer;

public class DifferentConsumers {

    static class Counter {
        private int counter;

        @NotModified
        public int getCounter() {
            return counter;
        }

        @Modified
        public int increment() {
            counter += 1;
            return counter;
        }
    }

    static class Consumers {
        private static final Consumer<Counter> incrementer = Counter::increment;

        @NotModified1
        private static final Consumer<Counter> printer = (@NotModified Counter counter) -> {
            System.out.println("Counts to " + counter.getCounter());
        };

        static void incrementAll(@Modified Collection<Counter> counters) {
            counters.forEach(incrementer);
        }

        static void println(@NotModified Collection<Counter> counters) {
            counters.forEach(printer);
        }

        static void doSomethingModifying(@Modified Collection<Counter> counters,
                                         Consumer<Counter> consumer) {
            counters.forEach(consumer); // .forEach(c -> consumer.accept(c))
        }

        static void doSomethingNonModifying(@NotModified Collection<Counter> counters,
                                            @NotModified1(type = AnnotationType.CONTRACT) Consumer<Counter> consumer) {
            counters.forEach(consumer);
        }

        static void forEach(@NotModified Collection<String> strings, Consumer<String> consumer) {
            strings.forEach(consumer);
        }
    }
}
