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

package org.e2immu.support;

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Only;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * On top of being freezable, this type prevents removing and overwriting key-value pairs.
 *
 * @param <K>
 * @param <V>
 */
@E2Container(after = "frozen")
public class SetOnceMap<K, V> extends Freezable {

    private final Map<K, V> map = new HashMap<>();

    @Only(before = "frozen")
    public void put(@NotNull K k, @NotNull V v) {
        Objects.requireNonNull(k);
        Objects.requireNonNull(v);
        ensureNotFrozen();
        if (isSet(k)) {
            throw new IllegalStateException("Already decided on " + k + ": have " + get(k) + ", want to write " + v);
        }
        map.put(k, v);
    }

    @NotNull
    public V get(K k) {
        if (!isSet(k)) throw new IllegalStateException("Not yet decided on " + k);
        return Objects.requireNonNull(map.get(k));
    }

    public int size() {
        return map.size();
    }

    public V getOtherwiseNull(K k) {
        return map.get(k);
    }

    public boolean isSet(K k) {
        return map.containsKey(k);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public Stream<Map.Entry<K, V>> stream() {
        return map.entrySet().stream();
    }

    @Only(before = "frozen")
    public void putAll(SetOnceMap<K, V> setOnceMap) {
        setOnceMap.stream().forEach(e -> put(e.getKey(), e.getValue()));
    }

    public V getOrDefault(K k, V v) {
        return map.getOrDefault(k, v);
    }

    public Map<K, V> toImmutableMap() {
        return Map.copyOf(map);
    }

    public V getOrCreate(K k, Function<K, V> generator) {
        V v = map.get(k);
        if (v != null) return v;
        V vv = generator.apply(k);
        map.put(k, vv);
        return vv;
    }
}
