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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

// extension class: implies @NotNull on first argument
@ExtensionClass(of = Map.class)
@E2Immutable // not container, addAll modifies argument
public class SMapList {

    private static final String NULL_KEY = "Adding null key to map-list";
    private static final String NULL_VALUE = "Adding null value to map-list";

    public static <A, B> Map<A, List<B>> create() {
        return new HashMap<>();
    }

    @NotModified
    @Constant(absent = true)
    public static <A, B> boolean addAll(@NotModified Map<A, List<B>> src, @Modified @NotNull Map<A, List<B>> destination) {
        boolean change = false;
        for (Entry<A, List<B>> e : src.entrySet()) {
            List<B> inDestination = destination.get(e.getKey());
            if (inDestination == null) {
                destination.put(e.getKey(), new LinkedList<>(e.getValue()));
                change = true;
            } else {
                if (inDestination.addAll(e.getValue())) {
                    change = true;
                }
            }
        }
        return change;
    }

    public static <A, B> boolean add(Map<A, List<B>> map, @NotNull @NotModified A a, @NotModified @NotNull B b) {
        if (a == null) {
            throw new IllegalArgumentException(NULL_KEY);
        }
        if (b == null) {
            throw new IllegalArgumentException(NULL_VALUE);
        }
        List<B> set = map.computeIfAbsent(a, k -> new LinkedList<>());
        return set.add(b);
    }

    public static <A, B> boolean add(Map<A, List<B>> map, @NotNull A a, @NotNull1 @NotModified List<B> bs) {
        if (a == null) {
            throw new IllegalArgumentException(NULL_KEY);
        }
        if (bs == null) {
            throw new IllegalArgumentException(NULL_VALUE);
        }
        List<B> list = map.computeIfAbsent(a, k -> new LinkedList<>());
        return list.addAll(bs);
    }

    @NotNull
    @NotModified
    @Constant(absent = true)
    public static <A, B> List<B> list(@NotNull @NotModified Map<A, List<B>> map, @NotNull A a) {
        if (a == null) {
            throw new IllegalArgumentException(NULL_KEY);
        }
        List<B> list = map.get(a);
        if (list == null) {
            return List.of();
        }
        return list;
    }

    @NotNull
    @E2Container
    public static <A, B> Map<A, List<B>> immutable(@NotModified @NotNull Map<A, List<B>> map) {
        Map<A, List<B>> tmp = new HashMap<>();
        for (Entry<A, List<B>> e : map.entrySet()) {
            List<B> is = List.copyOf(e.getValue());
            tmp.put(e.getKey(), is);
        }
        return Map.copyOf(tmp);
    }

    @NotNull
    @NotModified
    public static <A, B> Map<A, List<B>> copy(@NotNull @NotModified Map<A, List<B>> map) {
        Map<A, List<B>> tmp = new HashMap<>();
        for (Entry<A, List<B>> e : map.entrySet()) {
            List<B> set = new LinkedList<>(e.getValue());
            tmp.put(e.getKey(), set);
        }
        return tmp;
    }
}

