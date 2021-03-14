/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.util;

import com.google.common.collect.ImmutableList;
import org.e2immu.annotation.*;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

@UtilityClass
public class ListUtil {

    private ListUtil() {
    }

    @SafeVarargs
    @NotNull1
    public static <T> List<T> immutableConcat(@NotNull @NotModified Iterable<? extends T>... lists) {
        ImmutableList.Builder<T> builder = new ImmutableList.Builder<T>();
        for (Iterable<? extends T> list : lists) {
            builder.addAll(list);
        }
        return builder.build();
    }

    public static <T extends Comparable<? super T>> int compare(List<T> values1, List<T> values2) {
        Iterator<T> it2 = values2.iterator();
        for (T t1 : values1) {
            if (!it2.hasNext()) return 1;
            T t2 = it2.next();
            int c = t1.compareTo(t2);
            if (c != 0) return c;
        }
        if (it2.hasNext()) return -1;
        return 0;
    }

    public static <K, L> Stream<Pair<K, L>> joinLists(List<K> list1, List<L> list2) {
        Stream.Builder<Pair<K, L>> builder = Stream.builder();
        Iterator<L> it2 = list2.iterator();
        for (K t1 : list1) {
            if (!it2.hasNext()) break;
            L t2 = it2.next();
            builder.accept(new Pair<>(t1, t2));
        }
        return builder.build();
    }
}
