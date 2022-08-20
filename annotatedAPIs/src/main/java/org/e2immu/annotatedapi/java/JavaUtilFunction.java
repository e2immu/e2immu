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
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotNull;

import java.util.Comparator;
import java.util.function.Consumer;

public class JavaUtilFunction {

    public static final String PACKAGE_NAME = "java.util.function";

    // implicitly @Dependent, computed @Independent(hc = true)
    @Independent(hc = true)
    @Container
    interface Supplier$<T> {
        @Modified
        T get();
    }

    // without the annotation: implicitly @Dependent, computed @Dependent because of andThen
    @Independent(hc = true)
    interface Consumer$<T> {
        /*
        t is @Modified implicitly
         */
        @Modified
        void accept(T t);

        // default method, calling accept first, then "after"
        @NotNull
        Consumer<T> andThen(@NotNull Consumer<? super T> after);
    }

    @Independent(hc = true)
    interface Predicate$<T> {
        /*
         t is @Modified implicitly
         */
        @Modified
        boolean test(T t);
    }

    @Independent(hc = true)
    interface Function$<T, R> {
        /*
         t is @Modified implicitly
         */
        @Modified
        R apply(T t);

        @NotNull
        <V> java.util.function.Function<T, V> andThen(@NotNull java.util.function.Function<? super R, ? extends V> after);

        @NotNull
        <V> java.util.function.Function<V, R> compose(@NotNull java.util.function.Function<? super V, ? extends T> before);

        @NotNull
        <T> java.util.function.Function<T, T> identity();
    }

    @Independent(hc = true)
    interface BiFunction$<T, U, R> {

        @Modified
        R apply(T t, U u);

        @NotNull
        <V> java.util.function.BiFunction<T, U, V> andThen(@NotNull java.util.function.Function<? super R, ? extends V> after);
    }

    @Independent(hc = true)
    interface BinaryOperator$<T> extends java.util.function.BiFunction<T, T, T> {

        @NotNull
        <TT> java.util.function.BinaryOperator<T> maxBy(@NotNull Comparator<? super TT> comparator);

        @NotNull
        <TT> java.util.function.BinaryOperator<T> minBy(@NotNull Comparator<? super TT> comparator);
    }

    @Independent(hc = true)
    interface BiConsumer$<T, U> {
        @Modified
        void accept(T t, U u);

        @NotNull
        java.util.function.BiConsumer<T, U> andThen(@NotNull java.util.function.BiConsumer<? super T, ? super U> after);
    }

    @Independent(hc = true)
    interface ToIntFunction$<R> {
        @Modified
        int applyAsInt(R value);
    }

    @Independent(hc = true)
    interface ToIntBiFunction$<T, U> {
        @Modified
        int applyAsInt(T t, U u);
    }

    @Independent(hc = true)
    interface ToLongBiFunction$<T, U> {
        @Modified
        long applyAsLong(T t, U u);
    }

    @Independent(hc = true)
    interface ToDoubleBiFunction$<T, U> {
        @Modified
        double applyAsDouble(T t, U u);
    }

    @Independent(hc = true)
    interface ToLongFunction$<R> {
        @Modified
        long applyAsLong(R value);
    }

    @Independent(hc = true)
    interface ToDoubleFunction$<R> {
        @Modified
        double applyAsDouble(R value);
    }

    @Independent(hc = true)
    interface IntFunction$<R> {
        @Modified
        R apply(int value);
    }

    @Independent(hc = true)
    interface DoubleFunction$<R> {
        @Modified
        R apply(double value);
    }

    @Independent(hc = true)
    interface LongFunction$<R> {
        @Modified
        R apply(long value);
    }

    @Independent(hc = true)
    interface UnaryOperator$<R> {

    }

    @Independent(hc = true)
    interface ObjIntConsumer$<T> {
        @Modified
        void accept(T t, int i);
    }

    @Independent(hc = true)
    interface ObjLongConsumer$<T> {
        @Modified
        void accept(T t, long i);
    }

    @Independent(hc = true)
    interface ObjDoubleConsumer$<T> {
        @Modified
        void accept(T t, double i);
    }

}
