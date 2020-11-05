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
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Only;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

@E2Container(after = "freeze")
public class IncrementalMap<K> extends Freezable {

    @NotModified(after = "freeze")
    private final Map<K, Integer> map = new HashMap<>();
    public final BiPredicate<Integer, Integer> accept;

    public IncrementalMap(BiPredicate<Integer, Integer> biPredicate) {
        this.accept = biPredicate;
    }

    @Only(before = "freeze")
    public Integer put(@NotNull K k, int v) {
        Objects.requireNonNull(k);
        ensureNotFrozen();
        Integer current = map.get(k);
        // can go from -1 to 1, not from 1 to -1; can go from 1 to 2, from -1 to -2
        if (current != null && !accept.test(current, v))
            throw new UnsupportedOperationException("Not incremental? had " + current + ", now " + v + "; key " + k);
       return map.put(k, v);
    }

    @Only(before = "freeze")
    public void improve(@NotNull K k, int v) {
        Objects.requireNonNull(k);
        ensureNotFrozen();
        Integer current = map.get(k);
        // can go from -1 to 1, not from 1 to -1; can go from 1 to 2, from -1 to -2
        if (current == null || accept.test(current, v)) {
            map.put(k, v);
        }
    }

    @Only(before = "freeze")
    public void overwrite(@NotNull K k, int v) {
        Objects.requireNonNull(k);
        ensureNotFrozen();
        map.put(k, v);
    }

    @NotNull
    public int get(K k) {
        if (!isSet(k)) throw new UnsupportedOperationException("Not yet decided on " + k);
        return Objects.requireNonNull(map.get(k));
    }

    public boolean isSet(K k) {
        return map.containsKey(k);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public void visit(BiConsumer<K, Integer> consumer) {
        map.forEach(consumer);
    }

    public Stream<Map.Entry<K, Integer>> stream() {
        return map.entrySet().stream();
    }

    public int getOrDefault(K k, int i) {
        return map.getOrDefault(k, i);
    }

    public void putAll(IncrementalMap<K> other) {
        map.putAll(other.map);
    }

    public void copyFrom(IncrementalMap<K> other) {
        other.map.forEach(this::improve);
    }

    public Map<K, Integer> toImmutableMap() {
        return ImmutableMap.copyOf(map);
    }

    @Override
    public String toString() {
        return "IncrementalMap{" +
                "map=" + map +
                ", accept=" + accept +
                '}';
    }
}