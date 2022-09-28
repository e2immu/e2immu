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

package org.e2immu.analyser.util;

import org.e2immu.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

/*
The type would be an immutable container were it not for the mutable Map.Entry elements
returned by the Stream<Map.Entry<...>> result of stream().

The modifying methods 'put'
 */
@ImmutableContainer(hc = true)
public class UpgradableBooleanMap<T> {
    private static final UpgradableBooleanMap<?> EMPTY = new UpgradableBooleanMap<>();

    @NotNull
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
                return Objects::requireNonNull;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Set.of(Characteristics.CONCURRENT, Characteristics.IDENTITY_FINISH, Characteristics.UNORDERED);
            }
        };
    }

    private final Map<T, Boolean> map = new HashMap<>();

    @Independent
    @NotNull
    public static <T> UpgradableBooleanMap<T> of(T t, boolean b) {
        UpgradableBooleanMap<T> upgradableBooleanMap = new UpgradableBooleanMap<>();
        upgradableBooleanMap.put(t, b);
        return upgradableBooleanMap;
    }

    @Independent
    @NotNull
    public static <T> UpgradableBooleanMap<T> of(T t1, boolean b1, T t2, boolean b2) {
        UpgradableBooleanMap<T> upgradableBooleanMap = new UpgradableBooleanMap<>();
        upgradableBooleanMap.put(t1, b1);
        upgradableBooleanMap.put(t2, b2);
        return upgradableBooleanMap;
    }

    @SuppressWarnings("unchecked")
    @Independent
    public static <T> UpgradableBooleanMap<T> of() {
        return (UpgradableBooleanMap<T>) EMPTY;
    }

    @SafeVarargs
    @Independent
    public static <T> UpgradableBooleanMap<T> of(UpgradableBooleanMap<T>... maps) {
        UpgradableBooleanMap<T> result = new UpgradableBooleanMap<>();
        if (maps != null) {
            for (UpgradableBooleanMap<T> map : maps) {
                result.putAll(map);
            }
        }
        return result;
    }

    @Modified(construction = true)
    private void put(@Independent(hc = true) @NotNull T t, boolean b) {
        if (b || !map.containsKey(t)) {
            map.put(t, b);
        }
    }

    @Modified(construction = true)
    @Fluent
    private UpgradableBooleanMap<T> putAll(@Independent(hc = true) UpgradableBooleanMap<T> other) {
        other.stream().forEach(e -> this.put(e.getKey(), e.getValue()));
        return this;
    }

    @ImmutableContainer(hc = true)
    public record ImmutableEntry<T>(T t, boolean b) implements Map.Entry<T, Boolean> {

        public ImmutableEntry {
            Objects.requireNonNull(t);
        }

        @Override
        public T getKey() {
            return t;
        }

        @Override
        public Boolean getValue() {
            return b;
        }

        @Override
        public Boolean setValue(Boolean value) {
            assert value != null; // to stop e2immu from complaining about non-null
            throw new UnsupportedOperationException();
        }
    }

    @ImmutableContainer(hc = true)
    @NotNull // TODO implement (content = true)
    public Stream<Map.Entry<T, Boolean>> stream() {
        return map.entrySet().stream().map(e -> new ImmutableEntry<>(e.getKey(), e.getValue()));
    }

    public Boolean get(T t) {
        return map.get(t);
    }
}
