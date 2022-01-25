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

import org.e2immu.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class JavaUtilStream {

    static final String PACKAGE_NAME = "java.util.stream";

    interface Collector$<T, A, R> {
        @NotNull1
        @Modified
        Supplier<A> supplier();

        @NotNull
        @Modified
        BiConsumer<A, T> accumulator();

        @NotNull1
        @Modified
        BinaryOperator<A> combiner();

        @NotNull1
        @Modified
        Function<A, R> finisher();
    }

    @UtilityClass
    @Container
    static class Collectors$ {
        @NotNull1
        Collector<CharSequence, ?, String> joining() { return null; }

        @NotNull1
        Collector<CharSequence, ?, String> joining(@NotNull CharSequence delimiter) { return null; }

        @NotNull1
        <T> Collector<T, ?, Set<T>> toSet() { return null; };

        @NotNull1
        <T> Collector<T, ?, List<T>> toList() { return null; }

        @NotNull1
        static <T> Collector<T,?,List<T>> toUnmodifiableList() { return null; }

        @NotNull1
        static <T> Collector<T,?,Set<T>> toUnmodifiableSet() { return null; }
    }

    @E2Container
    interface IntStream$ {
        long count();

        int sum();

        @NotNull
        IntStream sorted();
    }

    @E2Container
    interface Stream$<T> {

        @NotNull
        @ERContainer
        <TT> Stream<TT> empty();

        @NotNull
        <TT> Stream<TT> of(@NotNull TT t);

        @NotNull
        <TT> Stream<TT> of(@NotNull TT... t);

        @NotNull
        <R> Stream<R> map(@Independent1 @NotNull Function<? super T, ? extends R> mapper);

        @NotNull
        <R> Stream<R> flatMap(@Independent1 @NotNull Function<? super T, ? extends Stream<? extends R>> mapper);

        @NotNull
        <R, A> R collect(@Independent1 @NotNull Collector<? super T, A, R> collector);

        @NotNull
        Stream<T> filter(@Independent1 @NotNull Predicate<? super T> predicate);

        @NotNull
        IntStream mapToInt(@Independent1 @NotNull ToIntFunction<? super T> mapper);

        @NotNull
        Optional<T> min(@Independent1 @NotNull Comparator<? super T> comparator);

        @NotNull
        Stream<T> sorted();

        @NotNull
        Optional<T> findAny();

        @NotNull
        Optional<T> findFirst();

        @NotNull
        void forEach(@Independent1 @NotNull Consumer<? super T> action);
    }

    @E2Container
    interface BaseStream$ {

    }
}
