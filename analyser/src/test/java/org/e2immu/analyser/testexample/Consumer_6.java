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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.Collection;
import java.util.function.Consumer;

/*
The difference with earlier tests is that with Counter, we will not be using
unbound parameter types as arguments to the SAM accept.

This is an important test to get right.
It requires annotated APIs, including an @IgnoreModification on System.out.
 */
public class Consumer_6 {

    @Container
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

    // MODIFYING

    private static final Consumer<Counter> incrementer = Counter::increment;

    static void incrementAll(@Modified Collection<Counter> counters) {
        counters.forEach(incrementer);
    }

    static void incrementAll2(@Modified Collection<Counter> counters) {
        counters.forEach(Counter::increment);
    }

    static void incrementAll2bis(@Modified Collection<Counter> counters) {
        counters.forEach(counter -> counter.increment());
    }

    static void incrementAll3(@Modified Collection<Counter> counters) {
        for (Counter counter : counters) {
            counter.increment();
        }
    }

    // NON-MODIFYING

    @Container
    private static final Consumer<Counter> printer = (@NotModified Counter counter) -> {
        System.out.println("Counts to " + counter.getCounter());
    };

    static void println(@NotModified Collection<Counter> counters) {
        counters.forEach(printer);
    }

    static void println2(@NotModified Collection<Counter> counters) {
        counters.forEach(counter -> System.out.println(counter.getCounter()));
    }

    static void println3(@NotModified Collection<Counter> counters) {
        for (Counter counter : counters) System.out.println(counter.getCounter());
    }

    // ABSTRACT

    static void doSomethingModifying(@Modified Collection<Counter> counters,
                                     Consumer<Counter> consumer) {
        counters.forEach(consumer); // .forEach(c -> consumer.accept(c))
    }

    // @Container is contracted here, with the meaning of enforcing the parameter of accept
    // to be non-modified.
    static void doSomethingNonModifying(@NotModified Collection<Counter> counters,
                                        @Container Consumer<Counter> consumer) {
        counters.forEach(consumer);
    }

    // even though accept is allowed to modify its parameter, it is not able to!
    static void doSomethingOnE2Immutable(@NotModified Collection<String> strings, Consumer<String> consumer) {
        strings.forEach(consumer);
    }

}
