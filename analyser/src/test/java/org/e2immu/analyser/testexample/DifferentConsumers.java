/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.testexample;

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
                                            @NotModified1(contract = true) Consumer<Counter> consumer) {
            counters.forEach(consumer);
        }

        static void forEach(@NotModified Collection<String> strings, Consumer<String> consumer) {
            strings.forEach(consumer);
        }
    }
}
