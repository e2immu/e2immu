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
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * On top of being freezable, this type prevents removing and overwriting key-value pairs.
 * The preconditions related to the overwriting are not part of the eventually immutable
 * aspect of the type.
 *
 * @param <V>
 */
@E2Container(after = "frozen")
public class AddOnceSet<V> extends Freezable {

    private final Map<V, V> set = new HashMap<>();

    public boolean add$Modification$Size(int post, int pre, V v) { return pre == 0 || !contains(v) ? post == 1: contains(v) ? post == pre: post >= pre && post <= pre+1; }
    public boolean add$Postcondition(V v) { return contains(v); }
    @Only(before = "frozen")
    public void add(@NotNull V v) {
        Objects.requireNonNull(v);
        ensureNotFrozen();
        if (contains(v)) throw new IllegalStateException("Already decided on " + v);
        set.put(v, v);
    }

    @NotNull
    public V get(V v) {
        if (!contains(v)) throw new IllegalStateException("Not yet decided on " + v);
        return Objects.requireNonNull(set.get(v)); // causes potential null pointer exception warning; that's OK
    }

    public void size$Aspect$Size() {}
    public int size() {
        return set.size();
    }

    public static boolean contains$Value$Size(int size, boolean retVal) { return size != 0 && retVal; }
    public boolean contains(V v) {
        return set.containsKey(v);
    }

    public static boolean isEmpty$Value$Size(int size) { return size == 0; }
    public boolean isEmpty() {
        return set.isEmpty();
    }

    public void visit(Consumer<V> consumer) {
        set.keySet().forEach(consumer);
    }

    public static int stream$Transfer$Size(int size) { return size; }
    public Stream<V> stream() {
        return set.keySet().stream();
    }

    public static int toImmutableSet$Transfer$Size(int size) { return size; }
    public Set<V> toImmutableSet() {
        return Set.copyOf(set.keySet());
    }
}
