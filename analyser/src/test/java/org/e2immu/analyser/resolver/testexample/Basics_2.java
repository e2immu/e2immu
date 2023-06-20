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

package org.e2immu.analyser.resolver.testexample;

import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.rare.IgnoreModifications;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Basics_2 {

    @ImmutableContainer // auto: (hc = true)
    interface HasSize {

        @NotModified
        int size();

        @NotModified
        default boolean isEmpty() {
            return size() == 0;
        }
    }

    @ImmutableContainer
    interface NonEmptyImmutableList<T> extends HasSize {

        @NotModified
        @Independent
        T first();

        /**
         * @param consumer It has the annotation {@link IgnoreModifications} implicitly, because {@link Consumer}
         *                 is an abstract type in the package {@link java.util.function}.
         */
        @NotModified
        void visit(Consumer<T> consumer);

        @ImmutableContainer("false")
        @Override
        default boolean isEmpty() {
            return false;
        }
    }

    @ImmutableContainer(hc = true)
    static class ImmutableArrayOfT<T> implements NonEmptyImmutableList<T> {

        private final T[] ts;

        @SuppressWarnings("unchecked")
        public ImmutableArrayOfT(int size, @Independent(hc = true) Supplier<T> generator) {
            ts = (T[]) new Object[size];
            Arrays.setAll(ts, i -> generator.get());
        }

        @Override
        public int size() {
            return ts.length;
        }

        @Override
        public T first() {
            return ts[0];
        }

        @NotModified
        public T get(int index) {
            return ts[index];
        }

        @Override
        public void visit(@Independent(hc = true) Consumer<T> consumer) {
            for (T t : ts) consumer.accept(t);
        }
    }
}
