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

import org.e2immu.annotation.*;

import java.util.function.Consumer;

public class FunctionalInterface_2 {

    @Container
    static class Counter {
        @Variable
        private int counter;

        @Modified
        public int increment() {
            return ++counter;
        }

        @Modified
        public int add(int step) {
            counter += step;
            return counter;
        }
    }

    @Modified // follows from potential modification in acceptMyCounter1
    private final Counter myCounter1 = new Counter();

    private final int i = 5;

    /*
    There are no other modifying methods, so acceptMyCounter1 is not immediately @Modified because
    it calls a SAM.

     */
    @Modified
    public void acceptMyCounter1(Consumer<Counter> consumer) {
        consumer.accept(myCounter1);
    }

    @Modified
    public void acceptInt1(Consumer<Integer> consumer) {
        consumer.accept(i);
    }

    public int getI() {
        return i;
    }

    /*
        The reasoning behind acceptMyCount2 being @NotModified:

        1. The consumer is @NotModified1, implying that the accept method does not modify myCounter2
        2. As a consequence, acceptMyCounter2 does not modify any fields
        3. As a consequence, myCounter2 is @NotModified
         */
    @NotModified
    private final Counter myCounter2 = new Counter();

    @Modified
    void acceptMyCounter2(@NotModified1(contract = true) Consumer<Counter> consumer) {
        consumer.accept(myCounter2);
    }


}
