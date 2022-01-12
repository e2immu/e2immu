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

package org.e2immu.analyser.parser.failing.testexample;

import org.e2immu.annotation.*;

import java.util.function.Consumer;

public class DynamicContainer_0 {

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

    /**
     * The value held by incrementer is a concrete implementation of a consumer, i.e., an anonymous
     * subtype. Because it has no fields, it is @E2Immutable. It is not a @Container, because of the modification to
     * the parameters in the implicit accept method.
     */
    @NotModified
    @E2Immutable
    private static final Consumer<Counter> incrementer = Counter::increment;

    /**
     * In its explicit form, the annotation @Modified is visible.
     */
    @NotModified
    @E2Immutable
    private static final Consumer<Counter> explicitIncrementer = new Consumer<Counter>() {
        @Override
        @NotModified
        public void accept(@Modified Counter counter) {
            counter.increment();
        }
    };

    /**
     * Again invisible; but now there is no change to the parameter of accept, so the anonymous type is a container.
     */
    @NotModified
    @E2Container
    private static final Consumer<Counter> printer = counter -> System.out.println("Have " + counter.getCounter());

    /**
     * ... and now visible. The concrete implementation makes no changes, the anonymous type is a container.
     */
    @NotModified
    @E2Container
    private static final Consumer<Counter> explicitPrinter = new Consumer<Counter>() {
        @Override
        @NotModified
        public void accept(@NotModified Counter counter) {
            System.out.println("Have " + counter.getCounter());
        }
    };

    /**
     * we will now enforce that consumer is a container
     */
    private void apply(@Container(contract = true) Consumer<Counter> consumer) {
        consumer.accept(myCounter);
    }

    public void useApply() {
        apply(printer); // should be fine
        apply(explicitPrinter);
        apply(incrementer); // should cause an ERROR; incrementer is not a container
        apply(explicitIncrementer); // should case an ERROR; explicit incrementer is not a container
    }

}
