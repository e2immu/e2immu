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
import org.e2immu.annotation.eventual.Only;
import org.e2immu.support.Freezable;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * In-house implementation of a directed graph that can be used to model the links between objects.
 *
 * @param <T>
 */
@ImmutableContainer(after = "frozen", hc = true)
public class WeightedGraph<T extends Comparable<? super T>, W extends WeightedGraph.Weight> extends Freezable {
    public interface WeightType<W extends Weight> {
        W neutral();
    }

    public interface Weight extends Comparable<Weight> {

    }

    private static class Node<T, W extends Weight> {
        Map<T, W> dependsOn;
        final T t;

        private Node(T t) {
            this.t = t;
        }
    }

    private final W neutral;

    public WeightedGraph(WeightType<W> weightType) {
        neutral = weightType.neutral();
    }

    @Modified
    private final Map<T, Node<T, W>> nodeMap = new TreeMap<>();

    @NotModified
    public int size() {
        return nodeMap.size();
    }

    @NotModified
    public boolean isEmpty() {
        return nodeMap.isEmpty();
    }

    @Independent(hc = true)
    public Map<T, W> links(@NotNull T t, boolean followDelayed) {
        Map<T, W> result = new TreeMap<>();
        result.put(t, neutral);
        recursivelyComputeLinks(t, result, followDelayed);
        return result;
    }

    @NotModified
    private void recursivelyComputeLinks(@NotNull T t, @NotNull Map<T, W> distanceToStartingPoint, boolean followDelayed) {
        Objects.requireNonNull(t);
        Node<T, W> node = nodeMap.get(t);

        // must be already present!
        W currentDistanceToT = distanceToStartingPoint.get(t);

        // do I have outgoing arrows?
        if (node != null && node.dependsOn != null) {

            // yes, opportunity (1) to improve distance computations, (2) to visit them
            node.dependsOn.forEach((n, d) -> {
                if (followDelayed || d.compareTo(neutral) >= 0) {
                    W distanceToN = d.compareTo(neutral) < 0 || currentDistanceToT.compareTo(neutral) < 0
                            ? min(d, currentDistanceToT) : max(currentDistanceToT, d);
                    W currentDistanceToN = distanceToStartingPoint.get(n);
                    if (currentDistanceToN == null) {
                        // we've not been at N before
                        distanceToStartingPoint.put(n, distanceToN);
                        recursivelyComputeLinks(n, distanceToStartingPoint, followDelayed);
                    } else {
                        W newDistanceToN = currentDistanceToN.compareTo(neutral) == 0 ? neutral : min(distanceToN, currentDistanceToN);
                        distanceToStartingPoint.put(n, newDistanceToN);
                    }
                } // else: ignore delayed links!
            });
        }
    }

    private W min(W w1, W w2) {
        return w1.compareTo(w2) <= 0 ? w1 : w2;
    }

    private W max(W w1, W w2) {
        return w1.compareTo(w2) <= 0 ? w2 : w1;
    }

    @NotModified(contract = true)
    public void visit(@NotNull @Independent(hc = true) BiConsumer<T, Map<T, W>> consumer) {
        nodeMap.values().forEach(n -> consumer.accept(n.t, n.dependsOn));
    }

    @NotNull
    @Modified
    @Only(before = "frozen")
    private Node<T, W> getOrCreate(@NotNull T t) {
        ensureNotFrozen();
        Objects.requireNonNull(t);
        Node<T, W> node = nodeMap.get(t);
        if (node == null) {
            node = new Node<>(t);
            nodeMap.put(t, node);
        }
        return node;
    }

    @Only(before = "frozen")
    @Modified
    public void addNode(@NotNull @Independent(hc = true) T t, @Independent(hc = true) @NotNull Map<T, W> dependsOn) {
        addNode(t, dependsOn, false);
    }

    @Only(before = "frozen")
    @Modified
    public void addNode(@NotNull @Independent(hc = true) T t,
                        @Independent(hc = true) @NotNull Map<T, W> dependsOn,
                        boolean bidirectional) {
        ensureNotFrozen();
        Node<T, W> node = getOrCreate(t);
        for (Map.Entry<T, W> e : dependsOn.entrySet()) {
            if (node.dependsOn == null) node.dependsOn = new TreeMap<>();
            node.dependsOn.put(e.getKey(), e.getValue());
            if (bidirectional) {
                Node<T, W> n = getOrCreate(e.getKey());
                if (n.dependsOn == null) n.dependsOn = new TreeMap<>();
                n.dependsOn.put(t, e.getValue());
            }
        }
    }
}
