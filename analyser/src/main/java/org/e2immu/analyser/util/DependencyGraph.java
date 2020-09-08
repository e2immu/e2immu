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

import org.e2immu.annotation.*;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * In-house implementation of a directed graph that can be used to model dependencies between objects.
 *
 * @param <T>
 */
@E2Container(after = "frozen")
public class DependencyGraph<T> extends Freezable {
    private static class Node<T> {
        List<T> dependsOn;
        final T t;

        private Node(T t) {
            this.t = t;
        }
    }

    @Modified
    private final Map<T, Node<T>> nodeMap = new HashMap<>();

    @NotModified
    public int size() {
        return nodeMap.size();
    }

    @NotModified
    public int relations() {
        return nodeMap.values().stream().mapToInt(node -> node.dependsOn == null ? 0 : node.dependsOn.size()).sum();
    }

    @NotModified
    public boolean isEmpty() {
        return nodeMap.isEmpty();
    }

    // return all transitive dependencies
    @Independent
    public Set<T> dependencies(@NotNull T t) {
        Set<T> result = new HashSet<>();
        Set<T> doNotVisitDoNotAdd = new HashSet<>();
        recursivelyComputeDependencies(t, result, doNotVisitDoNotAdd, false);
        return result;
    }

    // return all transitive dependencies, only return terminals
    @Independent
    public Set<T> dependenciesOnlyTerminals(@NotNull T t) {
        Set<T> result = new HashSet<>();
        Set<T> doNotVisitDoNotAdd = new HashSet<>();
        recursivelyComputeDependencies(t, result, doNotVisitDoNotAdd, true);
        return result;
    }

    @NotModified
    private void recursivelyComputeDependencies(@NotNull T t, @NotNull Set<T> result, @NotNull Set<T> doNotVisitDoNotAdd, boolean onlyTerminals) {
        Objects.requireNonNull(t);
        Node<T> node = nodeMap.get(t);
        if (onlyTerminals) {
            if (node == null || node.dependsOn == null || node.dependsOn.isEmpty()) {
                result.add(t);
                return;
            }
        } else {
            result.add(t);
        }
        doNotVisitDoNotAdd.add(t);
        if (node != null && node.dependsOn != null) {
            node.dependsOn.forEach(d -> {
                if (!doNotVisitDoNotAdd.contains(d)) {
                    recursivelyComputeDependencies(d, result, doNotVisitDoNotAdd, onlyTerminals);
                }
            });
        }
    }

    @NotNull
    @Modified
    @Only(before = "frozen")
    private Node<T> getOrCreate(@NotNull T t) {
        ensureNotFrozen();
        Objects.requireNonNull(t);
        Node<T> node = nodeMap.get(t);
        if (node == null) {
            node = new Node<>(t);
            nodeMap.put(t, node);
        }
        return node;
    }

    @NotModified(type = AnnotationType.CONTRACT)
    public void visit(@NotNull BiConsumer<T, List<T>> consumer) {
        nodeMap.values().forEach(n -> consumer.accept(n.t, n.dependsOn));
    }

    @Only(before = "frozen")
    @Modified
    public void addNode(@NotNull T t, @NotNull List<T> dependsOn) {
        ensureNotFrozen();
        Node<T> node = getOrCreate(t);
        for (T d : dependsOn) {
            if (node.dependsOn == null) node.dependsOn = new LinkedList<>();
            node.dependsOn.add(d);
        }
    }

    @Independent
    public List<T> sorted() {
        Map<T, Node<T>> toDo = new HashMap<>(nodeMap);
        Set<T> done = new HashSet<>();
        List<T> result = new ArrayList<>(nodeMap.size());
        while (!toDo.isEmpty()) {
            List<T> keys = new LinkedList<>();
            for (Map.Entry<T, Node<T>> entry : toDo.entrySet()) {
                List<T> dependencies = entry.getValue().dependsOn;
                if (dependencies == null || dependencies.isEmpty() || done.containsAll(dependencies)) {
                    keys.add(entry.getKey());
                    done.add(entry.getKey());
                }
            }
            if (keys.isEmpty()) {
                Map.Entry<T, Node<T>> toRemove = toDo.entrySet().stream().min(Comparator.comparingInt(e -> e.getValue().dependsOn.size())).orElseThrow();
                T key = toRemove.getKey();
                toDo.remove(key);
                done.add(key);
                result.add(key);
            } else {
                toDo.keySet().removeAll(keys);
                result.addAll(keys);
            }
        }
        return result;
    }

    public boolean equalTransitiveTerminals(@NotNull DependencyGraph<T> other) {
        if (nodeMap.size() != other.nodeMap.size()) return false;
        for (T node : nodeMap.keySet()) {
            Set<T> dependencies = dependenciesOnlyTerminals(node);
            Set<T> otherDependencies = other.dependenciesOnlyTerminals(node);
            if (!dependencies.equals(otherDependencies)) return false;
        }
        return true;
    }
}
