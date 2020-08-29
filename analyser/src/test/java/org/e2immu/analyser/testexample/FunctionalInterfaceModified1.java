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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;

import java.util.function.Function;
import java.util.function.Supplier;

public class FunctionalInterfaceModified1 {

    @Container
    static class Counter {
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

    @Modified // 4: follows from 3, myIncrementer is @Modified
    private final Counter myCounter = new Counter();

    @Modified // 1 & 2
    private final Supplier<Integer> getAndIncrement = myCounter::increment;

    @Modified // 2
    private final Supplier<Integer> explicitGetAndIncrement = new Supplier<Integer>() {
        @Override
        @Modified // 1
        public Integer get() {
            return myCounter.increment();
        }
    };

    @Modified // step 3
    public int myIncrementer() {
        return getAndIncrement.get();
    }

    @Modified // step 3
    public int myExplicitIncrementer() {
        return explicitGetAndIncrement.get();
    }

    // the following two functions are here to test that the translation from method reference to anonymous
    // type + method works properly

    @Modified
    public final Function<Integer, Integer> getAndAdd = myCounter::add;

    @Modified
    public final Function<Integer, Integer> getAndAdd2 = t -> myCounter.add(t);

    @Modified
    public final Function<Integer, Integer> getAndAdd3 = t -> {
        return myCounter.add(t);
    };
}
