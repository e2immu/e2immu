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

package org.e2immu.annotatedapi.java;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotNull;

import java.util.Comparator;

public class JavaUtilFunction {

    public static final String PACKAGE_NAME = "java.util.function";

    @Container
    interface Supplier$<T> {
        @Modified
        T get();
    }

    // implicitly @MutableModifiesArguments
    interface Consumer$<T> {
        @Modified
        void accept(T t);

        @NotNull
        java.util.function.Consumer<T> andThen(@NotNull java.util.function.Consumer<? super T> after);
    }

    interface Predicate$<T> {
        @Modified
        boolean test(T t);
    }

    interface Function$<T, R> {
        @Modified
        R apply(T t);

        @NotNull
        <V> java.util.function.Function<T, V> andThen(@NotNull java.util.function.Function<? super R, ? extends V> after);

        @NotNull
        <V> java.util.function.Function<V, R> compose(@NotNull java.util.function.Function<? super V, ? extends T> before);

        @NotNull
        <T> java.util.function.Function<T, T> identity();
    }

    interface BiFunction$<T, U, R> {

        @Modified
        R apply(T t, U u);

        @NotNull
        <V> java.util.function.BiFunction<T, U, V> andThen(@NotNull java.util.function.Function<? super R, ? extends V> after);
    }

    interface BinaryOperator$<T> extends java.util.function.BiFunction<T, T, T> {

        @NotNull
        <TT> java.util.function.BinaryOperator<T> maxBy(@NotNull Comparator<? super TT> comparator);

        @NotNull
        <TT> java.util.function.BinaryOperator<T> minBy(@NotNull Comparator<? super TT> comparator);
    }

    interface BiConsumer$<T, U> {
        @Modified
        void accept(T t, U u);

        @NotNull
        java.util.function.BiConsumer<T, U> andThen(@NotNull java.util.function.BiConsumer<? super T, ? super U> after);
    }

    interface ToIntFunction$<R> {
        @Modified
        int applyAsInt(R value);
    }

    interface IntFunction$<R> {
        @Modified
        R apply(int value);
    }
}
