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

package org.e2immu.analyser.analyser;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class Properties {
    public static final Properties EMPTY = Properties.frozen();

    private final Map<Property, DV> map;

    private Properties(Map<Property, DV> map) {
        this.map = map;
        //assert map.values().stream().noneMatch(v -> v == DV.MIN_INT_DV);
    }

    private static Properties frozen() {
        return new Properties(Map.of());
    }

    public static Properties writable() {
        return new Properties(new HashMap<>());
    }

    public static Properties of(Map<Property, DV> map) {
        return new Properties(Map.copyOf(map));
    }

    public static Properties ofWritable(Map<Property, DV> map) {
        return new Properties(new HashMap<>(map));
    }

    public boolean isDone(Property property) {
        DV v = map.get(property);
        return v != null && v.isDone();
    }

    public DV getOrDefaultNull(Property property) {
        Objects.requireNonNull(property);
        return map.get(property);
    }

    public DV getOrDefault(Property property, DV defaultValue) {
        Objects.requireNonNull(property);
        Objects.requireNonNull(defaultValue);
        return map.getOrDefault(property, defaultValue);
    }

    public DV get(Property property) {
        Objects.requireNonNull(property);
        DV dv = map.get(property);
        Objects.requireNonNull(dv);
        return dv;
    }

    public void overwrite(Property property, DV dv) {
        map.put(property, dv);
    }

    public void put(Property property, DV dv) {
        Objects.requireNonNull(dv);
        Objects.requireNonNull(property);
        DV inMap = map.get(property);
        if (inMap == null || inMap.isDelayed()) {
            map.put(property, dv);
        } else if (!inMap.equals(dv)) {
            throw new IllegalArgumentException("Changing value of " + property + " from " + inMap + " to " + dv);
        }
    }

    public Properties combine(Properties other) {
        if (map.isEmpty()) return other;
        map.putAll(other.map);
        return this;
    }

    public CausesOfDelay delays() {
        return map.values().stream().map(DV::causesOfDelay).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
    }

    public boolean containsKey(Property property) {
        return map.containsKey(property);
    }

    public Stream<Map.Entry<Property, DV>> stream() {
        return map.entrySet().stream();
    }

    public Map<Property, DV> toImmutableMap() {
        return Map.copyOf(map);
    }

    public Properties immutable() {
        return new Properties(Map.copyOf(map));
    }

    public static Collector<Property, Properties, Properties> collect(Function<Property, DV> mapper) {
        return collect(mapper, false);
    }

    public static Collector<Property, Properties, Properties> collect(Function<Property, DV> mapper, boolean writable) {
        return new Collector<>() {
            @Override
            public Supplier<Properties> supplier() {
                return Properties::writable;
            }

            @Override
            public BiConsumer<Properties, Property> accumulator() {
                return (p, e) -> p.put(e, mapper.apply(e));
            }

            @Override
            public BinaryOperator<Properties> combiner() {
                return Properties::combine;
            }

            @Override
            public Function<Properties, Properties> finisher() {
                return writable ? p -> p : Properties::immutable;
            }

            @Override
            public Set<Characteristics> characteristics() {
                if (writable) {
                    return Set.of(Characteristics.UNORDERED, Characteristics.IDENTITY_FINISH);
                }
                return Set.of(Characteristics.UNORDERED);
            }
        };
    }

    public void merge(Property key, DV value, BiFunction<DV, DV, DV> remapping) {
        map.merge(key, value, remapping);
    }

    public void removeAll(Set<Property> properties) {
        map.keySet().removeAll(properties);
    }

    public DV remove(Property property) {
        return map.remove(property);
    }

    @Override
    public String toString() {
        return map.toString();
    }

    public void replaceDelaysByMinimalValue() {
        for (Map.Entry<Property, DV> entry : map.entrySet()) {
            if (entry.getValue().isDelayed()) {
                entry.setValue(entry.getKey().falseDv);
            }
        }
    }

    public Properties merge(Properties valueProperties) {
        Map<Property, DV> merged = new HashMap<>(map);
        valueProperties.stream().forEach(e -> merged.merge(e.getKey(), e.getValue(), DV::min));
        return of(merged);
    }
}
