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

import com.google.common.collect.ImmutableSet;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Only;
import org.e2immu.annotation.Size;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * On top of being freezable, this type prevents removing and overwriting key-value pairs.
 *
 * @param <V>
 */
@E2Container(after = "frozen,map")
public class AddOnceSet<V> extends Freezable {

    private final Map<V, V> set = new HashMap<>();

    @Only(before = "frozen,set")
    @Size(min = 1)
    public void add(@NotNull V v) {
        Objects.requireNonNull(v);
        ensureNotFrozen();
        if (contains(v)) throw new UnsupportedOperationException("Already decided on " + v);
        set.put(v, v);
    }

    @NotNull
    @Only(after = "map")
    public V get(V v) {
        if (!contains(v)) throw new UnsupportedOperationException("Not yet decided on " + v);
        return Objects.requireNonNull(set.get(v));
    }

    @Size(min = 0)
    public int size() {
        return set.size();
    }

    public boolean contains(V v) {
        return set.containsKey(v);
    }

    @Size(equals = 0)
    public boolean isEmpty() {
        return set.isEmpty();
    }

    public void visit(Consumer<V> consumer) {
        set.keySet().forEach(consumer);
    }

    @Size(copy = true)
    public Stream<V> stream() {
        return set.keySet().stream();
    }

    public Set<V> toImmutableSet() {
        return ImmutableSet.copyOf(set.keySet());
    }
}
