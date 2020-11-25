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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.e2immu.annotation.*;

import java.util.*;

public class ComGoogleCommonCollect {
    final static String PACKAGE_NAME="com.google.common.collect";

    @E2Container
    static abstract class ImmutableCollection$<E> extends AbstractCollection<E> {

        int asList$Transfer$Size(int size) { return size; }
        @NotNull
        @Independent
        @NotModified
        ImmutableList<E> asList() { return null;  }

    }

    @E2Container
    static class ImmutableList$<E> { // extends immutable collection

        @Container(builds = ImmutableList.class)
        public static class Builder<E> {

            int build$Transfer$Size(int size) { return size; }
            @NotNull
            @Independent
            @NotModified
            public ImmutableList<E> build() { return null; }
        }

        int copyOf$Transfer$Size(int size) { return size; }
        @NotNull
        @E2Container
        static <E> ImmutableList<E> copyOf(@NotNull Iterable<? extends E> iterable) { return null; }
    }


    @E2Container
    static class ImmutableSet$<E> {

        @Container(builds = ImmutableSet.class)
        public static class Builder<E> {

            int build$Transfer$Size(int size) { return size; }
            @NotNull
            @Independent
            @NotModified
            public ImmutableSet<E> build() { return null; }
        }

        int copyOf$Transfer$Size(int size) { return size; }
        @NotNull
        @E2Container
        static <E> ImmutableSet<E> copyOf(@NotNull Collection<? extends E> collection) { return null; }
    }

}
