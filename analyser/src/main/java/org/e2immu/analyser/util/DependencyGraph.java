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
import org.e2immu.support.Freezable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
        recursivelyComputeDependencies(t, result);
        return result;
    }

    @NotModified
    private void recursivelyComputeDependencies(@NotNull T t, @NotNull Set<T> result) {
        Objects.requireNonNull(t);
        Node<T> node = nodeMap.get(t);

        result.add(t);

        if (node != null && node.dependsOn != null) {
            node.dependsOn.forEach(d -> {
                if (!result.contains(d)) {
                    recursivelyComputeDependencies(d, result);
                }
            });
        }
    }

    @Independent
    public Set<T> dependenciesWithoutStartingPoint(@NotNull T t) {
        Set<T> result = new HashSet<>();
        recursivelyComputeDependenciesWithoutStartingPoint(t, result);
        return result;
    }


    @NotModified
    private void recursivelyComputeDependenciesWithoutStartingPoint(@NotNull T t, @NotNull Set<T> result) {
        Objects.requireNonNull(t);
        Node<T> node = nodeMap.get(t);

        if (node != null && node.dependsOn != null) {
            node.dependsOn.forEach(d -> {
                if (!result.contains(d)) {
                    result.add(d);
                    recursivelyComputeDependenciesWithoutStartingPoint(d, result);
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

    @NotModified(contract = true)
    public void visit(@NotNull BiConsumer<T, List<T>> consumer) {
        nodeMap.values().forEach(n -> consumer.accept(n.t, n.dependsOn));
    }


    @Only(before = "frozen")
    @Modified
    public void addNode(@NotNull T t, @NotNull Collection<T> dependsOn) {
        addNode(t, dependsOn, false);
    }

    @Only(before = "frozen")
    @Modified
    public void addNode(@NotNull T t, @NotNull Collection<T> dependsOn, boolean bidirectional) {
        ensureNotFrozen();
        Node<T> node = getOrCreate(t);
        for (T d : dependsOn) {
            if (node.dependsOn == null) node.dependsOn = new LinkedList<>();
            node.dependsOn.add(d);
            if (bidirectional) {
                Node<T> n = getOrCreate(d);
                if (n.dependsOn == null) n.dependsOn = new LinkedList<>();
                n.dependsOn.add(t);
            }
        }
    }

    @Independent
    public List<T> sorted() {
        return sorted(t -> {
        });
    }

    @Independent
    public List<T> sorted(Consumer<T> reportPartOfCycle) {
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
                // we have a cycle, break by taking one with a minimal number of dependencies
                Map.Entry<T, Node<T>> toRemove = toDo.entrySet().stream().min(Comparator.comparingInt(e -> e.getValue().dependsOn.size())).orElseThrow();
                T key = toRemove.getKey();
                toDo.remove(key);
                done.add(key);
                result.add(key);
                reportPartOfCycle.accept(key);
            } else {
                toDo.keySet().removeAll(keys);
                result.addAll(keys);
            }
        }
        return result;
    }
}
