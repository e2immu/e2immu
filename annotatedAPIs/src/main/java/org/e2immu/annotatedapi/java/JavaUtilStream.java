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

import org.e2immu.annotation.*;
import org.e2immu.annotation.rare.Finalizer;
import org.e2immu.annotation.type.UtilityClass;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public class JavaUtilStream {

    static final String PACKAGE_NAME = "java.util.stream";

    interface Collector$<T, A, R> {
        @NotNull(content = true)
        @Modified
        Supplier<A> supplier();

        @NotNull
        @Modified
        BiConsumer<A, T> accumulator();

        @NotNull(content = true)
        @Modified
        BinaryOperator<A> combiner();

        @NotNull(content = true)
        @Modified
        Function<A, R> finisher();
    }

    @UtilityClass
    @Container
    static class Collectors$ {
        @NotNull(content = true)
        Collector<CharSequence, ?, String> joining() {
            return null;
        }

        @NotNull(content = true)
        Collector<CharSequence, ?, String> joining(@NotNull CharSequence delimiter) {
            return null;
        }

        @NotNull(content = true)
        <T> Collector<T, ?, Set<T>> toSet() {
            return null;
        }

        @NotNull(content = true)
        <T> Collector<T, ?, List<T>> toList() {
            return null;
        }

        @NotNull(content = true)
        static <T> Collector<T, ?, List<T>> toUnmodifiableList() {
            return null;
        }

        @NotNull(content = true)
        static <T> Collector<T, ?, Set<T>> toUnmodifiableSet() {
            return null;
        }
    }

    @Container
    interface IntStream$ {
        @Modified
        @Finalizer
        long count();

        @Modified
        @Finalizer
        int sum();

        // dependent, not modified
        @NotNull
        IntStream sorted();

        interface IntMapMultiConsumer {
            @Modified
            void accept(int i, IntConsumer ic);
        }

        @NotNull
        IntStream of(int i);

        @Independent(hc = true)
        <X> Stream<X> mapToObj(@NotNull IntFunction<? extends X> mapper);
    }

    @Container
    interface DoubleStream$ {
        @Modified
        @Finalizer
        long count();

        @Modified
        @Finalizer
        double sum();

        @NotNull
        DoubleStream sorted();

        @Independent
        interface DoubleMapMultiConsumer {
            @Modified
            void accept(double d, DoubleConsumer dc);
        }
    }

    @Container
    interface LongStream$ {
        @Modified
        @Finalizer
        long count();

        @Modified
        @Finalizer
        long sum();

        @NotNull
        LongStream sorted();

        @Independent
        interface LongMapMultiConsumer {
            @Modified
            void accept(long l, LongConsumer lc);
        }
    }

    @Container
    interface Stream$<T> {

        @NotNull
        Stream.Builder<T> builder();

        /*
         Factory method, result dependent on parameters
         */
        @NotNull
        <TT> Stream<TT> concat(@NotNull Stream<? extends TT> s1, @NotNull Stream<? extends TT> s2);

        /*
         Independent, yet mutable
         */
        @NotNull
        @Independent
        <TT> Stream<TT> empty();

        /*
         Factory method, the hidden content in the result comes from the parameter
         */
        @NotNull
        @Independent(hc = true)
        <TT> Stream<TT> of(@NotNull TT t);

        /*
         Factory method, the hidden content in the result comes from the parameter
         */
        @NotNull
        @Independent(hc = true)
        <TT> Stream<TT> of(@NotNull TT... t);

        /*
         The resulting stream is dependent on the object stream, but the method is not modifying.
         Note that the functional interface implies @IgnoreModifications, which allows modifications external to the type,
         */
        @NotNull
        <R> Stream<R> map(@NotNull Function<? super T, ? extends R> mapper);

        @NotNull
        <R> Stream<R> flatMap(@NotNull Function<? super T, ? extends Stream<? extends R>> mapper);

        @NotNull
        @Modified
        @Finalizer
        <R, A> R collect(@NotNull Collector<? super T, A, R> collector);

        @NotNull
        Stream<T> filter(@NotNull Predicate<? super T> predicate);

        @NotNull
        IntStream mapToInt(@NotNull ToIntFunction<? super T> mapper);

        @NotNull
        @Modified
        @Finalizer
        Optional<T> min(@NotNull Comparator<? super T> comparator);

        @NotNull
        Stream<T> sorted();

        @NotNull
        Stream<T> sorted(@NotNull Comparator<? super T> comparator);

        @NotNull
        @Modified
        @Finalizer
        Optional<T> findAny();

        @NotNull
        @Modified
        @Finalizer
        Optional<T> findFirst();

        /*
         The action is perfectly allowed to modify the hidden content presented to it, as opposed to the mappers.
         */
        @NotNull
        @Modified
        @Finalizer
        void forEach(@Independent(hc = true) @NotNull Consumer<? super T> action);

        @NotNull
        @Modified
        @Finalizer
        @Independent(hc = true)
        @ImmutableContainer(hc = true)
        List<T> toList();

        @NotNull
        @Modified
        @Finalizer
        @Independent(hc = true)
        <A> A[] toArray(IntFunction<A[]> generator);
    }

    @ImmutableContainer
    interface BaseStream$ {

    }

    @UtilityClass
    @Container
    interface StreamSupport$ {
        <T> Stream<T> stream(Spliterator<T> spliterator, boolean parallel);
    }
}
