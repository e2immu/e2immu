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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/*
situation: consumer applied to non-parameter of implicitly immutable type
 */
public class AbstractTypeAsParameter_4 {

    @E2Container
    static class MySet<X> {
        private final Set<X> set = new HashSet<>();

        @Independent
        public MySet(X x) {
            set.add(x);
        }

        @NotModified
        public void forEach(@NotModified1 Consumer<X> consumer) {
            for (X x : set) consumer.accept(x);
        }

        @NotModified
        public Stream<X> stream() {
            return set.stream();
        }
    }

    @Container
    static class Y {
        private final String string;
        private int i;

        public Y(@NotNull String string) {
            this.string = string;
        }

        public int getI() {
            return i;
        }

        public int increment() {
            return ++i;
        }

        public String getString() {
            return string;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Y y = (Y) o;
            return string.equals(y.string);
        }

        @Override
        public int hashCode() {
            return Objects.hash(string);
        }

        @Override
        public String toString() {
            return string + "->" + i;
        }
    }

    public static void print(@NotModified MySet<Y> ySet) {
        ySet.forEach(System.out::println); // nan-modifying method implies no modification on ySet
    }

    public static void incrementAll(@NotModified MySet<Y> ySet) {
        ySet.forEach(Y::increment); // ERROR: not allowed to use modifying methods as argument
    }
}
