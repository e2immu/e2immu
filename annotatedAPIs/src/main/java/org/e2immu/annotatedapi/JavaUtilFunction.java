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

package org.e2immu.annotatedapi;

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.Comparator;

public class JavaUtilFunction {

    public static final String PACKAGE_NAME = "java.util.function";

// it is important to note that get cannot be marked @Independent, because a supplier
// is a simple way to link an object from the outside world to a field.

    @FunctionalInterface
    public interface Supplier$<T> {
        @Modified
        T get();
    }

// it is important to note that accept cannot be marked @Independent, because a consumer
// is a simple way to expose a field to the outside world.

    @FunctionalInterface
    public interface Consumer$<T> {
        @Modified
        void accept(T t);

        default java.util.function.Consumer<T> andThen(@NotNull java.util.function.Consumer<? super T> after) {
            return null;
        }
    }

    @FunctionalInterface
    public interface Predicate$<T> {
        @Modified
        boolean test(T t);
    }

    @FunctionalInterface
    public interface Function$<T, R> {
        @Modified
        R apply(T t);

        @NotNull
        @NotModified
        default <V> java.util.function.Function<T, V> andThen(@NotNull java.util.function.Function<? super R, ? extends V> after) {
            return null;
        }

        @NotNull
        @NotModified
        default <V> java.util.function.Function<V, R> compose(@NotNull java.util.function.Function<? super V, ? extends T> before) {
            return null;
        }

        @NotNull
        @NotModified
        static <T> java.util.function.Function<T, T> identity() {
            return null;
        }
    }

    @FunctionalInterface
    public interface BiFunction$<T, U, R> {

        @Modified
        R apply(T t, U u);

        default <V> java.util.function.BiFunction<T, U, V> andThen(@NotNull java.util.function.Function<? super R, ? extends V> after) {
            return null;
        }
    }

    @FunctionalInterface
    public interface BinaryOperator$<T> extends java.util.function.BiFunction<T, T, T> {

        @NotModified
        static <T> java.util.function.BinaryOperator<T> maxBy(@NotNull Comparator<? super T> comparator) {
            return null;
        }

        @NotModified
        static <T> java.util.function.BinaryOperator<T> minBy(@NotNull Comparator<? super T> comparator) {
            return null;
        }
    }

    @FunctionalInterface
    public interface BiConsumer$<T, U> {
        @Modified
        void accept(T t, U u);

        @NotNull
        default java.util.function.BiConsumer<T, U> andThen(@NotNull java.util.function.BiConsumer<? super T, ? super U> after) {
            return null;
        }
    }

    @FunctionalInterface
    public interface ToIntFunction$<R> {
        @Modified
        int applyAsInt(R value);
    }

    @FunctionalInterface
    public interface IntFunction$<R> {
        @Modified
        R apply(int value);
    }
}
