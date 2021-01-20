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

import com.google.common.collect.ImmutableMap;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Only;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * On top of being freezable, this type prevents removing and overwriting key-value pairs.
 *
 * @param <K>
 * @param <V>
 */
@E2Container(after = "frozen,map")
public class SetOnceMap<K, V> extends Freezable {

    private final Map<K, V> map = new HashMap<>();

    @Only(before = "frozen,map")
    public void put(@NotNull K k, @NotNull V v) {
        Objects.requireNonNull(k);
        Objects.requireNonNull(v);
        ensureNotFrozen();
        if (isSet(k)) throw new UnsupportedOperationException("Already decided on " + k);
        map.put(k, v);
    }

    @NotNull
    @Only(after = "map")
    public V get(K k) {
        if (!isSet(k)) throw new UnsupportedOperationException("Not yet decided on " + k);
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

    public void visit(BiConsumer<K, V> consumer) {
        map.forEach(consumer);
    }

    public Stream<Map.Entry<K, V>> stream() {
        return map.entrySet().stream();
    }

    public void putAll(SetOnceMap<K, V> setOnceMap) {
        setOnceMap.stream().forEach(e -> put(e.getKey(), e.getValue()));
    }

    @Only(before = "frozen,map")
    public void putAll(SetOnceMap<K, V> setOnceMap, boolean complainWhenAlreadySet) {
        setOnceMap.stream().forEach(e -> {
            if (complainWhenAlreadySet || !isSet(e.getKey())) put(e.getKey(), e.getValue());
        });
    }

    public V getOrDefault(K k, V v) {
        return map.getOrDefault(k, v);
    }

    public Map<K, V> toImmutableMap() {
        return ImmutableMap.copyOf(map);
    }

    public V getOrCreate(K k, Function<K, V> generator) {
        V v = map.get(k);
        if (v != null) return v;
        V vv = generator.apply(k);
        map.put(k, vv);
        return vv;
    }

    public void addAll(SetOnceMap<K, V> other) {
        other.stream().forEach(e -> put(e.getKey(), e.getValue()));
    }
}
