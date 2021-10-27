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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * In-house implementation of a directed graph that can be used to model the links between objects.
 *
 * @param <T>
 */
@E2Container(after = "frozen")
public class WeightedGraph<T> extends Freezable {
    private static class Node<T> {
        Map<T, Integer> dependsOn;
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
    public boolean isEmpty() {
        return nodeMap.isEmpty();
    }

    @Independent
    public Map<T, Integer> links(@NotNull T t, boolean followDelayed) {
        Map<T, Integer> result = new HashMap<>();
        result.put(t, 0);
        recursivelyComputeLinks(t, result, followDelayed);
        return result;
    }

    @NotModified
    private void recursivelyComputeLinks(@NotNull T t, @NotNull Map<T, Integer> distanceToStartingPoint, boolean followDelayed) {
        Objects.requireNonNull(t);
        Node<T> node = nodeMap.get(t);

        // must be already present!
        int currentDistanceToT = distanceToStartingPoint.get(t);

        // do I have outgoing arrows?
        if (node != null && node.dependsOn != null) {

            // yes, opportunity (1) to improve distance computations, (2) to visit them
            node.dependsOn.forEach((n, d) -> {
                if (followDelayed || d >= 0) {
                    int distanceToN = d < 0 ? d : Math.max(currentDistanceToT, d);
                    Integer currentDistanceToN = distanceToStartingPoint.get(n);
                    if (currentDistanceToN == null) {
                        // we've not been at N before
                        distanceToStartingPoint.put(n, distanceToN);
                        recursivelyComputeLinks(n, distanceToStartingPoint, followDelayed);
                    } else {
                        int newDistanceToN = currentDistanceToN == 0 ? 0 : Math.min(distanceToN, currentDistanceToN);
                        distanceToStartingPoint.put(n, newDistanceToN);
                    }
                } // else: ignore delayed links!
            });
        }
    }

    @NotModified(contract = true)
    public void visit(@NotNull BiConsumer<T, Map<T, Integer>> consumer) {
        nodeMap.values().forEach(n -> consumer.accept(n.t, n.dependsOn));
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

    @Only(before = "frozen")
    @Modified
    public void addNode(@NotNull T t, @NotNull Map<T, Integer> dependsOn) {
        addNode(t, dependsOn, false);
    }

    @Only(before = "frozen")
    @Modified
    public void addNode(@NotNull T t, @NotNull Map<T, Integer> dependsOn, boolean bidirectional) {
        ensureNotFrozen();
        Node<T> node = getOrCreate(t);
        for (Map.Entry<T, Integer> e : dependsOn.entrySet()) {
            if (node.dependsOn == null) node.dependsOn = new HashMap<>();
            node.dependsOn.put(e.getKey(), e.getValue());
            if (bidirectional) {
                Node<T> n = getOrCreate(e.getKey());
                if (n.dependsOn == null) n.dependsOn = new HashMap<>();
                n.dependsOn.put(t, e.getValue());
            }
        }
    }
}
