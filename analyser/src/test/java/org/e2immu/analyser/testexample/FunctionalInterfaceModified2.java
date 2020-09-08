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

public class FunctionalInterfaceModified2 {

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

    /*
    There are no other modifying methods, so acceptMyCounter1 is not immediately @Modified because
    it calls an abstract method.

    The field myCounter1 is NOT support data; however, it is fully modifiable.
    We'd be contradicting ourselves; TODO not independent, so field is modified
    TODO problem of support data
     */
    @Modified
    public void acceptMyCounter1(Consumer<Counter> consumer) {
        consumer.accept(myCounter1);
    }

    /*
    The reasoning behind acceptMyCount2 being @NotModified:

    1. The consumer is @NotModified1, implying that the accept method does not modify myCounter2
    2. As a consequence, acceptMyCounter2 does not modify any fields
    3. As a consequence, myCounter2 is @NotModified
     */
    @NotModified
    private final Counter myCounter2 = new Counter();

    @NotModified
    void acceptMyCounter2(@NotModified1(type = CONTRACT) Consumer<Counter> consumer) {
        consumer.accept(myCounter2);
    }


}
