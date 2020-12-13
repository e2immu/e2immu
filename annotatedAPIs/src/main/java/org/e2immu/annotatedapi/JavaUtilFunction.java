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

package org.e2immu.annotatedapi;

import org.e2immu.annotation.NotNull;

import java.util.Comparator;

public class JavaUtilFunction {

    public static final String PACKAGE_NAME = "java.util.function";

// it is important to note that get cannot be marked @Independent, because a supplier
// is a simple way to link an object from the outside world to a field.

    @FunctionalInterface
    public interface Supplier$<T> {
        T get();
    }

// it is important to note that accept cannot be marked @Independent, because a consumer
// is a simple way to expose a field to the outside world.

    @FunctionalInterface
    public interface Consumer$<T> {
        void accept(T t);

        default java.util.function.Consumer<T> andThen(@NotNull java.util.function.Consumer<? super T> after) {
            return null;
        }
    }

    @FunctionalInterface
    public interface Predicate$<T> {
        boolean test(T t);
    }

    @FunctionalInterface
    public interface Function$<T, R> {
        R apply(T t);

        @NotNull
        default <V> java.util.function.Function<T, V> andThen(@NotNull java.util.function.Function<? super R, ? extends V> after) {
            return null;
        }

        @NotNull
        default <V> java.util.function.Function<V, R> compose(@NotNull java.util.function.Function<? super V, ? extends T> before) {
            return null;
        }

        @NotNull
        static <T> java.util.function.Function<T, T> identity() {
            return null;
        }
    }

    @FunctionalInterface
    public interface BiFunction$<T, U, R> {

        R apply(T t, U u);

        default <V> java.util.function.BiFunction<T, U, V> andThen(@NotNull java.util.function.Function<? super R, ? extends V> after) {
            return null;
        }
    }

    @FunctionalInterface
    public interface BinaryOperator$<T> extends java.util.function.BiFunction<T, T, T> {

        static <T> java.util.function.BinaryOperator<T> maxBy(@NotNull Comparator<? super T> comparator) {
            return null;
        }

        static <T> java.util.function.BinaryOperator<T> minBy(@NotNull Comparator<? super T> comparator) {
            return null;
        }
    }

    @FunctionalInterface
    public interface BiConsumer$<T, U> {
        void accept(T t, U u);

        @NotNull
        default java.util.function.BiConsumer<T, U> andThen(@NotNull java.util.function.BiConsumer<? super T, ? super U> after) {
            return null;
        }
    }

    @FunctionalInterface
    public interface ToIntFunction$<R> {
        int applyAsInt(R value);
    }

    @FunctionalInterface
    public interface IntFunction$<R> {
        R apply(int value);
    }
}
