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

    public static final String PACKAGE_NAME = "java.util.stream";

    public interface Collector$<T, A, R> {
        @NotNull1
        Supplier<A> supplier();

        @NotNull
        BiConsumer<A, T> accumulator();

        @NotNull1
        BinaryOperator<A> combiner();

        @NotNull1
        Function<A, R> finisher();
    }

    @UtilityClass
    @Container
    public class Collectors$ {
        @NotNull1
        Collector<CharSequence, ?, String> joining() {
            return null;
        }

        @NotNull1
        Collector<CharSequence, ?, String> joining(@NotNull CharSequence delimiter) {
            return null;
        }

        @NotNull1
        <T> Collector<T, ?, Set<T>> toSet() {
            return null;
        }

        @NotNull1
        <T> Collector<T, ?, List<T>> toList() {
            return null;
        }
    }

    @E2Container
    public interface IntStream$ {
        long count();

        int sum();

        @NotNull
        IntStream sorted();
    }

    @E2Container
    public interface Stream$<T> {

        @NotNull
        static <T> Stream<T> empty() { return  null; }

        @NotNull
        static <T> Stream<T> of(@NotNull T t){ return  null; }

        @NotNull
        static <T> Stream<T> of(@NotNull T... t){ return  null; }

        long count();

        @NotNull
        <R> Stream<R> map(@NotNull Function<? super T, ? extends R> mapper);

        @NotNull
        <R, A> R collect(@NotNull Collector<? super T, A, R> collector);

        @NotNull
        Stream<T> filter(@NotNull Predicate<? super T> predicate);

        @NotNull
        IntStream mapToInt(@NotNull ToIntFunction<? super T> mapper);

        @NotNull
        Optional<T> min(@NotNull Comparator<? super T> comparator);

        @NotNull
        Stream<T> sorted();

        @NotNull
        Optional<T> findAny();

        @NotNull
        Optional<T> findFirst();
    }

}
