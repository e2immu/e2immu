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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/*
situation: consumer applied to non-parameter of implicitly immutable type
 */
public class AbstractTypeAsParameter_0 {

    interface MyConsumer<T> {
        // UNMARKED
        void accept(T t); // PARAMETER T unmarked
    }

    @E1Container
    static class MySet<X> {
        private final Set<X> set = new HashSet<>();

        @Modified
        public void add(X x) {
            set.add(x);
        }

        @NotModified // because X is implicitly immutable, the parameter of accept cannot touch it wrt MySet
        public void forEach(@NotModified @PropagateModification MyConsumer<X> consumer) { // because forEach calls an unmarked method on consumer (and no other modifying method)
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

    public static void incrementAll(@Modified MySet<Y> ySet) {
        ySet.forEach(Y::increment); // modifying method implies modification in ySet
    }
}