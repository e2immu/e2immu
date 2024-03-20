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

import java.util.*;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Properties implements Comparable<Properties> {
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

    public Properties overwrite(Property property, DV dv) {
        map.put(property, dv);
        return this;
    }

    public boolean put(Property property, DV dv) {
        return put(property, dv, true);
    }

    // return progress
    public boolean put(Property property, DV dv, boolean complain) {
        Objects.requireNonNull(dv);
        Objects.requireNonNull(property);
        DV inMap = map.get(property);
        if (inMap == null || inMap.isDelayed()) {
            map.put(property, dv);
            return dv.isDone();
        }
        if (complain && !inMap.equals(dv)) {
            throw new IllegalArgumentException("Changing value of " + property + " from " + inMap + " to " + dv);
        }
        return false;
    }

    public Properties combine(Properties other) {
        if (map.isEmpty()) return other;
        map.putAll(other.map);
        return this;
    }

    public Properties combineSafely(Properties other) {
        if (map.isEmpty()) return other;
        if (other.map.isEmpty()) return this;
        Map<Property, DV> newMap = new HashMap<>(map);
        newMap.putAll(other.map);
        return Properties.of(newMap);
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

    public Properties mergeVariableAccessReport(Properties other) {
        Map<Property, DV> merged = VariableInfo.mergeVariableAccessReport(map, other.map);
        return of(merged);
    }

    public Properties copy() {
        return Properties.of(map);
    }

    @Override
    public int compareTo(Properties o) {
        return compareMaps(map, o.map);
    }

    public static <T extends Comparable<? super T>, D extends Comparable<? super D>> int compareMaps(Map<T, D> map1, Map<T, D> map2) {
        int c = map1.size() - map2.size();
        if (c != 0) return c;
        // same size
        int differentValue = 0;
        for (Map.Entry<T, D> e : map1.entrySet()) {
            D dv = map2.get(e.getKey());
            if (dv != null && differentValue == 0) {
                // are there different values?
                differentValue = e.getValue().compareTo(dv);
            }
            if (dv == null) {
                // different keys
                return compareKeys(map1.keySet(), map2.keySet());
            }
        }
        return differentValue;
    }

    private static <T extends Comparable<? super T>> int compareKeys(Set<T> set1, Set<T> set2) {
        TreeSet<T> treeSet1 = new TreeSet<>(set1);
        TreeSet<T> treeSet2 = new TreeSet<>(set2);
        Iterator<T> it1 = treeSet1.iterator();
        Iterator<T> it2 = treeSet2.iterator();
        while (it1.hasNext()) {
            assert it2.hasNext();
            int d = it1.next().compareTo(it2.next());
            if (d != 0) return d;
        }
        return 0;
    }

    public String sortedToString() {
        return stream().map(Object::toString).sorted().collect(Collectors.joining(", "));
    }

    public Properties delay(Predicate<Property> toDelay, Predicate<Property> toFalse, CausesOfDelay causesOfDelay) {
        Map<Property, DV> newMap = new HashMap<>();
        for (Map.Entry<Property, DV> entry : map.entrySet()) {
            Property p = entry.getKey();
            if (toFalse.test(p)) {
                newMap.put(p, p.falseDv);
            } else if (toDelay.test(p)) {
                newMap.put(p, causesOfDelay);
            } else {
                newMap.put(p, entry.getValue());
            }
        }
        return Properties.of(newMap);
    }
}
