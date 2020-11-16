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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.e2immu.annotation.*;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ComGoogleCommonCollect {
    final static String PACKAGE_NAME="com.google.common.collect";

    static class ImmutableCollection$<E> {
        ImmutableList<E> asList() { return null;  }

        boolean contains(Object object) { return false; }

        int toArray$Transfer$Size(int size) { return size; }
        public Object[] toArray() { return null; }

        public <T> T[] toArray(T[] other) { return null; }
    }

    @E2Container
    static class ImmutableList$<E> {

        @Container(builds = ImmutableList.class)
        public static class Builder<E> {
            @Fluent
            public Builder<E> add(E... elements) {return this; }

            @Fluent
            public Builder<E> add(E element) { return this; }

            @Fluent
            public Builder<E> addAll(@NotNull Iterable<? extends E> iterable) { return this; }

            @Fluent
            public Builder<E> addAll(@NotNull Iterator<? extends E> iterator) { return this; }

            @NotNull
            @Independent
            @NotModified
            public ImmutableList<E> build() { return null; }
        }

        @NotNull
        @E2Container
        static <E> ImmutableList<E> copyOf(@NotNull Iterable<? extends E> iterable) { return null; }
    }


    @E2Container
    static class ImmutableSet$<E> {

        @Container(builds = ImmutableSet.class)
        public static class Builder<E> {
            @Fluent
            public Builder<E> add(E... elements) {return this; }

            @Fluent
            public Builder<E> add(E element) { return this; }

            @Fluent
            public Builder<E> addAll(@NotNull Iterable<? extends E> iterable) { return this; }

            @Fluent
            public Builder<E> addAll(@NotNull Iterator<? extends E> iterator) { return this; }

            @NotNull
            @Independent
            @NotModified
            public ImmutableSet<E> build() { return null; }
        }

        @NotNull
        @E2Container
        static <E> ImmutableSet<E> copyOf(@NotNull Set<? extends E> iterable) { return null; }
    }

}
