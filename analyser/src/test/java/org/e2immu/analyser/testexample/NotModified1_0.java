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
import org.e2immu.annotation.NotModified1;

import java.util.function.Consumer;

public class NotModified1_0 {

    @Container
    static class Counter {
        private int counter;

        @NotModified
        public int getCounter() {
            return counter;
        }

        @Modified
        public int increment() {
            return ++counter;
        }
    }

    @NotModified
    private final Counter myCounter = new Counter();

    @NotModified
    @NotModified1(absent = true)
    private static final Consumer<Counter> incrementer = Counter::increment;

    @NotModified
    @NotModified1(absent = true)
    private static final Consumer<Counter> explicitIncrementer = new Consumer<Counter>() {
        @Override
        @NotModified
        public void accept(@Modified Counter counter) {
            counter.increment();
        }
    };

    @NotModified
    @NotModified1
    private static final Consumer<Counter> printer = counter -> System.out.println("Have " + counter.getCounter());

    @NotModified
    @NotModified1
    private static final Consumer<Counter> explicitPrinter = new Consumer<Counter>() {
        @Override
        @NotModified
        public void accept(@NotModified Counter counter) {
            System.out.println("Have " + counter.getCounter());
        }
    };

    private void apply(@NotModified1(contract = true) Consumer<Counter> consumer) {
        consumer.accept(myCounter);
    }

    public void useApply() {
        apply(printer); // should be fine
        apply(explicitPrinter);
        apply(incrementer); // should cause an ERROR
        apply(explicitIncrementer); // should case an ERROR
    }

}
