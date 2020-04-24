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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.e2immu.annotation.*;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

// extension class: implies @NotNull on first argument
@ExtensionClass(of = Map.class)
public class SMapList {

    private static final String NULL_KEY = "Adding null key to map-list";
    private static final String NULL_VALUE = "Adding null value to map-list";

    public static <A, B> Map<A, List<B>> create() {
        return new HashMap<>();
    }

    @NotNull
    @Independent
    @NotModified
    @Constant(type = AnnotationType.VERIFY_ABSENT)
    public static <A, B> boolean addAll(Map<A, List<B>> src, @NotNull Map<A, List<B>> dest) {
        boolean change = false;
        for (Entry<A, List<B>> e : src.entrySet()) {
            List<B> inDest = dest.get(e.getKey());
            if (inDest == null) {
                dest.put(e.getKey(), new LinkedList<>(e.getValue()));
                change = true;
            } else {
                if (inDest.addAll(e.getValue())) {
                    change = true;
                }
            }
        }
        return change;
    }

    public static <A, B> boolean add(Map<A, List<B>> map, @NotNull A a, @NotNull B b) {
        if (a == null) {
            throw new IllegalArgumentException(NULL_KEY);
        }
        if (b == null) {
            throw new IllegalArgumentException(NULL_VALUE);
        }
        List<B> set = map.computeIfAbsent(a, k -> new LinkedList<>());
        return set.add(b);
    }

    public static <A, B> boolean add(Map<A, List<B>> map, @NotNull A a, @NotNull @NotModified List<B> bs) {
        if (a == null) {
            throw new IllegalArgumentException(NULL_KEY);
        }
        if (bs == null) {
            throw new IllegalArgumentException(NULL_VALUE);
        }
        List<B> set = map.computeIfAbsent(a, k -> new LinkedList<>());
        return set.addAll(bs);
    }

    @NotNull
    @Independent
    @NotModified
    @Linked(type = AnnotationType.VERIFY_ABSENT) // NULL_KEY is E2Immu
    @Constant(type = AnnotationType.VERIFY_ABSENT)
    public static <A, B> List<B> list(@NotNull Map<A, List<B>> map, @NotNull A a) {
        if (a == null) {
            throw new IllegalArgumentException(NULL_KEY);
        }
        List<B> set = map.get(a);
        if (set == null) {
            return List.of();
        }
        return set;
    }

    @NotNull
    @Independent
    public static <A, B> Map<A, List<B>> immutable(Map<A, List<B>> map) {
        Map<A, ImmutableList<B>> tmp = new HashMap<>();
        for (Entry<A, List<B>> e : map.entrySet()) {
            ImmutableList<B> is = new ImmutableList.Builder<B>().addAll(e.getValue()).build();
            tmp.put(e.getKey(), is);
        }
        return new ImmutableMap.Builder<A, List<B>>().putAll(tmp).build();
    }

    @NotNull
    @Independent
    public static <A, B> Map<A, List<B>> copy(Map<A, List<B>> map) {
        Map<A, List<B>> tmp = new HashMap<>();
        for (Entry<A, List<B>> e : map.entrySet()) {
            List<B> set = new LinkedList<>(e.getValue());
            tmp.put(e.getKey(), set);
        }
        return tmp;
    }
}

