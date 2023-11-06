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

package org.e2immu.analyser.model.expression.util;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class TranslationCollectors<T> {
    public static <T> Collector<T, List<T>, List<T>> toList(List<T> original) {
        return new Collector<>() {
            boolean changes;

            @Override
            public Supplier<List<T>> supplier() {
                return () -> new ArrayList<T>(original.size());
            }

            @Override
            public BiConsumer<List<T>, T> accumulator() {
                return (list, t) -> {
                    T inOriginal = original.get(list.size());
                    changes |= inOriginal != t;
                    list.add(t);
                };
            }

            @Override
            public BinaryOperator<List<T>> combiner() {
                return (l1, l2) -> {
                    throw new UnsupportedOperationException("Combiner not implemented");
                };
            }

            @Override
            public Function<List<T>, List<T>> finisher() {
                // we also test for different size: this allows for the removal of objects outside a strict
                // Translation setting (see ParameterizedType.replaceTypeBounds)
                return list -> changes || list.size() != original.size() ? List.copyOf(list) : original;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Set.of();
            }
        };
    }

    public static <T> Collector<T, Set<T>, Set<T>> toSet(Set<T> original) {
        return new Collector<>() {
            boolean changes;

            @Override
            public Supplier<Set<T>> supplier() {
                return () -> new HashSet<>(original.size());
            }

            @Override
            public BiConsumer<Set<T>, T> accumulator() {
                return (set, t) -> {
                    boolean inOriginal = original.contains(t);
                    changes |= !inOriginal;
                    set.add(t);
                };
            }

            @Override
            public BinaryOperator<Set<T>> combiner() {
                return (l1, l2) -> {
                    throw new UnsupportedOperationException("Combiner not implemented");
                };
            }

            @Override
            public Function<Set<T>, Set<T>> finisher() {
                return set -> changes || set.size() != original.size() ? Set.copyOf(set) : original;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Set.of();
            }
        };
    }


    public static <K, V> Collector<Map.Entry<K, V>, Map<K, V>, Map<K, V>> toMap(Map<K, V> original) {
        return new Collector<>() {
            boolean changes;

            @Override
            public Supplier<Map<K, V>> supplier() {
                return () -> new HashMap<>(original.size());
            }

            @Override
            public BiConsumer<Map<K, V>, Map.Entry<K, V>> accumulator() {
                return (map, entry) -> {
                    K key = entry.getKey();
                    V inOriginal = original.get(key);
                    V value = entry.getValue();
                    changes |= !Objects.equals(inOriginal, value);
                    map.put(key, value);
                };
            }

            @Override
            public BinaryOperator<Map<K, V>> combiner() {
                return (l1, l2) -> {
                    throw new UnsupportedOperationException("Combiner not implemented");
                };
            }

            @Override
            public Function<Map<K, V>, Map<K, V>> finisher() {
                return map -> changes || map.size() != original.size() ? Map.copyOf(map) : original;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Set.of();
            }
        };
    }

}
