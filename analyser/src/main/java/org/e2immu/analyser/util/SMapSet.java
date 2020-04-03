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
import com.google.common.collect.ImmutableSet;
import org.e2immu.annotation.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

// extension class: implies @NullNotAllowed on first argument
@ExtensionClass
public class SMapSet {

    private static final String NULL_KEY = "Adding null key to map-set";
    private static final String NULL_VALUE = "Adding null value to map-set";

    public static <A, B> Map<A, Set<B>> create() {
        return new HashMap<>();
    }

    public static <A, B> boolean addAll(Map<A, Set<B>> src, @NullNotAllowed Map<A, Set<B>> dest) {
        boolean change = false;
        for (Entry<A, Set<B>> e : src.entrySet()) {
            Set<B> inDest = dest.get(e.getKey());
            if (inDest == null) {
                dest.put(e.getKey(), new HashSet<>(e.getValue()));
                change = true;
            } else {
                if (inDest.addAll(e.getValue())) {
                    change = true;
                }
            }
        }
        return change;
    }

    public static <A, B> boolean add(Map<A, Set<B>> map, @NullNotAllowed A a, @NullNotAllowed B b) {
        if (a == null) {
            throw new IllegalArgumentException(NULL_KEY);
        }
        if (b == null) {
            throw new IllegalArgumentException(NULL_VALUE);
        }
        Set<B> set = map.computeIfAbsent(a, k -> new HashSet<>());
        return set.add(b);
    }

    public static <A, B> boolean add(Map<A, Set<B>> map, @NullNotAllowed A a, @NullNotAllowed @NotModified Set<B> bs) {
        if (a == null) {
            throw new IllegalArgumentException(NULL_KEY);
        }
        if (bs == null) {
            throw new IllegalArgumentException(NULL_VALUE);
        }
        Set<B> set = map.computeIfAbsent(a, k -> new HashSet<>());
        return set.addAll(bs);
    }

    public static <A, B> Set<B> set(Map<A, Set<B>> map, @NullNotAllowed A a) {
        if (a == null) {
            throw new IllegalArgumentException(NULL_KEY);
        }
        Set<B> set = map.get(a);
        if (set == null) {
            return Set.of();
        }
        return set;
    }

    @NotNull
    @Independent
    public static <A, B> Map<A, Set<B>> immutable(Map<A, Set<B>> map) {
        Map<A, ImmutableSet<B>> tmp = new HashMap<>();
        for (Entry<A, Set<B>> e : map.entrySet()) {
            ImmutableSet<B> is = new ImmutableSet.Builder<B>().addAll(e.getValue()).build();
            tmp.put(e.getKey(), is);
        }
        return new ImmutableMap.Builder<A, Set<B>>().putAll(tmp).build();
    }

    @NotNull
    @Independent
    public static <A, B> Map<A, Set<B>> copy(Map<A, Set<B>> map) {
        Map<A, Set<B>> tmp = new HashMap<>();
        for (Entry<A, Set<B>> e : map.entrySet()) {
            Set<B> set = new HashSet<>(e.getValue());
            tmp.put(e.getKey(), set);
        }
        return tmp;
    }
}

