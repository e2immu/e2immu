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
