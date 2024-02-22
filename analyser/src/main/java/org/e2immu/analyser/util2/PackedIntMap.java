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

package org.e2immu.analyser.util2;

import org.e2immu.annotation.*;
import org.e2immu.graph.analyser.PackedInt;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

@ImmutableContainer(hc = true)
public class PackedIntMap<T> implements Iterable<Map.Entry<T, Integer>> {
    private static final PackedIntMap<?> EMPTY = new PackedIntMap<>();

    @NotNull
    public static <T> Collector<? super Map.Entry<T, Integer>, PackedIntMap<T>, PackedIntMap<T>> collector() {
        return new Collector<>() {
            @Override
            public Supplier<PackedIntMap<T>> supplier() {
                return PackedIntMap::new;
            }

            @Override
            public BiConsumer<PackedIntMap<T>, Map.Entry<T, Integer>> accumulator() {
                return (map, e) -> map.put(e.getKey(), e.getValue());
            }

            @Override
            public BinaryOperator<PackedIntMap<T>> combiner() {
                return PackedIntMap::putAll;
            }

            @Override
            public Function<PackedIntMap<T>, PackedIntMap<T>> finisher() {
                return Objects::requireNonNull;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Set.of(Characteristics.CONCURRENT, Characteristics.IDENTITY_FINISH, Characteristics.UNORDERED);
            }
        };
    }

    private final Map<T, Integer> map = new HashMap<>();

    @Independent
    @NotNull
    public static <T> PackedIntMap<T> of(T t, int b) {
        PackedIntMap<T> upgradableIntegerMap = new PackedIntMap<>();
        upgradableIntegerMap.put(t, b);
        return upgradableIntegerMap;
    }

    @Independent // IMPROVE should be hc=true
    @NotNull
    public static <T> PackedIntMap<T> of(T t1, int b1, T t2, int b2) {
        PackedIntMap<T> upgradableIntegerMap = new PackedIntMap<>();
        upgradableIntegerMap.put(t1, b1);
        upgradableIntegerMap.put(t2, b2);
        return upgradableIntegerMap;
    }

    @SuppressWarnings("unchecked")
    @Independent
    public static <T> PackedIntMap<T> of() {
        return (PackedIntMap<T>) EMPTY;
    }

    @SafeVarargs
    @Independent(hc = true)
    public static <T> PackedIntMap<T> of(PackedIntMap<T>... maps) {
        PackedIntMap<T> result = new PackedIntMap<>();
        if (maps != null) {
            for (PackedIntMap<T> map : maps) {
                result.putAll(map);
            }
        }
        return result;
    }

    @Modified(construction = true)
    private void put(@Independent(hc = true) @NotNull T t, int b) {
        map.merge(t, b, PackedInt::sum);
    }

    @Modified(construction = true)
    @Fluent
    private PackedIntMap<T> putAll(@Independent(hc = true) PackedIntMap<T> other) {
        other.stream().forEach(e -> this.put(e.getKey(), e.getValue()));
        return this;
    }

    @Override
    public Iterator<Map.Entry<T, Integer>> iterator() {
        return map.entrySet().iterator();
    }

    @ImmutableContainer(hc = true)
    public record ImmutableEntry<T>(T t, int b) implements Map.Entry<T, Integer> {

        public ImmutableEntry {
            Objects.requireNonNull(t);
        }

        @Override
        public T getKey() {
            return t;
        }

        @Override
        public Integer getValue() {
            return b;
        }

        @Override
        public Integer setValue(Integer value) {
            assert value != null; // to stop e2immu from complaining about non-null
            throw new UnsupportedOperationException();
        }
    }

    @Independent(hc = true)
    @NotNull // TODO implement (content = true)
    public Stream<Map.Entry<T, Integer>> stream() {
        return map.entrySet().stream().map(e -> new ImmutableEntry<>(e.getKey(), e.getValue()));
    }

    public Integer get(T t) {
        return map.get(t);
    }
}