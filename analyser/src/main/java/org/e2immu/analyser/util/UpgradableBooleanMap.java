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

package org.e2immu.analyser.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class UpgradableBooleanMap<T> {
    private static final UpgradableBooleanMap<?> EMPTY = new UpgradableBooleanMap<>();

    public static <T> Collector<? super Map.Entry<T, Boolean>, UpgradableBooleanMap<T>, UpgradableBooleanMap<T>> collector() {
        return new Collector<>() {
            @Override
            public Supplier<UpgradableBooleanMap<T>> supplier() {
                return UpgradableBooleanMap::new;
            }

            @Override
            public BiConsumer<UpgradableBooleanMap<T>, Map.Entry<T, Boolean>> accumulator() {
                return (map, e) -> map.put(e.getKey(), e.getValue());
            }

            @Override
            public BinaryOperator<UpgradableBooleanMap<T>> combiner() {
                return UpgradableBooleanMap::putAll;
            }

            @Override
            public Function<UpgradableBooleanMap<T>, UpgradableBooleanMap<T>> finisher() {
                return t -> t;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Set.of(Characteristics.CONCURRENT, Characteristics.IDENTITY_FINISH, Characteristics.UNORDERED);
            }
        };
    }

    private final Map<T, Boolean> map = new HashMap<>();

    public static <T> UpgradableBooleanMap<T> of(T t, boolean b) {
        UpgradableBooleanMap<T> upgradableBooleanMap = new UpgradableBooleanMap<>();
        upgradableBooleanMap.put(t, b);
        return upgradableBooleanMap;
    }

    public static <T> UpgradableBooleanMap<T> of(T t1, boolean b1, T t2, boolean b2) {
        UpgradableBooleanMap<T> upgradableBooleanMap = new UpgradableBooleanMap<>();
        upgradableBooleanMap.put(t1, b1);
        upgradableBooleanMap.put(t2, b2);
        return upgradableBooleanMap;
    }

    public static <T> UpgradableBooleanMap<T> of() {
        return (UpgradableBooleanMap<T>) EMPTY;
    }

    @SafeVarargs
    public static <T> UpgradableBooleanMap<T> of(UpgradableBooleanMap<T>... maps) {
        UpgradableBooleanMap<T> result = new UpgradableBooleanMap<>();
        if (maps != null) {
            for (UpgradableBooleanMap<T> map : maps) {
                result.putAll(map);
            }
        }
        return result;
    }

    private void put(T t, boolean b) {
        if (b || !map.containsKey(t)) {
            map.put(t, b);
        }
    }

    private UpgradableBooleanMap<T> putAll(UpgradableBooleanMap<T> other) {
        other.stream().forEach(e -> put(e.getKey(), e.getValue()));
        return this;
    }

    public Stream<Map.Entry<T, Boolean>> stream() {
        return map.entrySet().stream();
    }

    public Boolean get(T t) {
        return map.get(t);
    }
}
