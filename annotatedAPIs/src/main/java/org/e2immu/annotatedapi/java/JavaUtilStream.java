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
import org.e2immu.annotation.type.UtilityClass;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    @ImmutableContainer
    @Independent
    interface IntStream$ {
        long count();

        int sum();

        @NotNull
        IntStream sorted();

        interface IntMapMultiConsumer {
            @Modified
            void accept(int i, IntConsumer ic);
        }
    }

    @ImmutableContainer
    @Independent
    interface DoubleStream$ {
        long count();

        double sum();

        @NotNull
        DoubleStream sorted();

        interface DoubleMapMultiConsumer {
            @Modified
            void accept(double d, DoubleConsumer dc);
        }
    }

    @ImmutableContainer
    @Independent
    interface LongStream$ {
        long count();

        long sum();

        @NotNull
        LongStream sorted();

        interface LongMapMultiConsumer {
            @Modified
            void accept(long l, LongConsumer lc);
        }
    }

    /*
     Analyser adds hc=true, because Stream is an interface.
     */
    @ImmutableContainer
    interface Stream$<T> {

        /*
         Factory method, the hidden content in the result comes from the parameters
         */
        @NotNull
        @ImmutableContainer(hc = true)
        <TT> Stream<TT> concat(@NotNull Stream<? extends TT> s1, @NotNull Stream<? extends TT> s2);

        /*
         Independent!
         */
        @NotNull
        <TT> Stream<TT> empty();

        /*
         Factory method, the hidden content in the result comes from the parameter
         */
        @NotNull
        @ImmutableContainer(hc = true)
        <TT> Stream<TT> of(@NotNull TT t);

        /*
         Factory method, the hidden content in the result comes from the parameter
         */
        @NotNull
        @ImmutableContainer(hc = true)
        <TT> Stream<TT> of(@NotNull TT... t);

        /*
         The mapper is not supposed to modify the hidden content received as argument in the "apply()" method.
         For that reason, we add @Independent rather than @Independent(hc=true).
         Note that the functional interface implies @IgnoreModifications, which allows modifications external to the type,
         */
        @NotNull
        @Independent(hc = true)
        <R> Stream<R> map(@NotNull Function<? super T, ? extends R> mapper);

        /*
         The mapper is not supposed to modify the hidden content presented to it.
         For that reason, we do not add @Independent(hc=true). FIXME independent means we can access but not modify
         */
        @NotNull
        @Independent(hc = true)
        <R> Stream<R> flatMap(@NotNull Function<? super T, ? extends Stream<? extends R>> mapper);

        @NotNull
        @Independent(hc = true)
        <R, A> R collect(@NotNull Collector<? super T, A, R> collector);

        @NotNull
        @Independent(hc = true)
        Stream<T> filter(@NotNull Predicate<? super T> predicate);

        /*
         @Independent!
         */
        @NotNull
        IntStream mapToInt(@NotNull ToIntFunction<? super T> mapper);

        @NotNull
        @Independent(hc = true)
        Optional<T> min(@NotNull Comparator<? super T> comparator);

        @NotNull
        @Independent(hc = true)
        Stream<T> sorted();

        @NotNull
        @Independent(hc = true)
        Stream<T> sorted(@NotNull Comparator<? super T> comparator);

        @NotNull
        @Independent(hc = true)
        Optional<T> findAny();

        @NotNull
        @Independent(hc = true)
        Optional<T> findFirst();

        /*
         The action is perfectly allowed to modify the hidden content presented to it, as opposed to the mappers.
         */
        @NotNull
        @NotModified
        void forEach(@Independent(hc = true) @NotNull Consumer<? super T> action);

        @NotNull
        @Independent(hc = true)
        List<T> toList();
    }

    @ImmutableContainer
    interface BaseStream$ {

    }


}
