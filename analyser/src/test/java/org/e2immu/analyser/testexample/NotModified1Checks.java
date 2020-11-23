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

public class NotModified1Checks {

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
