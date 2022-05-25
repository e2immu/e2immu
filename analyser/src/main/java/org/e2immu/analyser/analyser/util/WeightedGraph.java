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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.annotation.*;
import org.e2immu.support.Freezable;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;

import static org.e2immu.analyser.analyser.LinkedVariables.STATICALLY_ASSIGNED_DV;

/**
 * In-house implementation of a directed graph that is used to model the links between objects.
 * A distance of 0 (STATICALLY_ASSIGNED) is always kept, even across delays.
 */
@E2Container(after = "frozen")
public class WeightedGraph extends Freezable {


    private static class Node {
        Map<Variable, DV> dependsOn;
        final Variable variable;

        private Node(Variable v) {
            variable = v;
        }
    }

    @Modified
    private final Map<Variable, Node> nodeMap = new TreeMap<>();

    @NotModified
    public int size() {
        return nodeMap.size();
    }

    @NotModified
    public boolean isEmpty() {
        return nodeMap.isEmpty();
    }

    @Independent
    public Map<Variable, DV> links(@NotNull Variable v, DV maxWeight, boolean followDelayed) {
        Map<Variable, DV> result = new TreeMap<>();
        result.put(v, STATICALLY_ASSIGNED_DV);
        recursivelyComputeLinks(v, result, maxWeight, followDelayed);
        return result;
    }

    @NotModified
    private void recursivelyComputeLinks(@NotNull Variable v,
                                         @NotNull Map<Variable, DV> distanceToStartingPoint,
                                         DV maxValueIncl,
                                         boolean followDelayed) {
        Objects.requireNonNull(v);
        Node node = nodeMap.get(v);

        // must be already present!
        DV currentDistanceToV = distanceToStartingPoint.get(v);

        // do I have outgoing arrows?
        if (node != null && node.dependsOn != null) {

            // yes, opportunity (1) to improve distance computations, (2) to visit them
            node.dependsOn.forEach((n, d) -> {
                if (d.isDelayed() && followDelayed || d.isDone() && (maxValueIncl == null ||  d.le(maxValueIncl))) {
                    DV distanceToN = max(currentDistanceToV, d);
                    DV currentDistanceToN = distanceToStartingPoint.get(n);
                    if (currentDistanceToN == null) {
                        // we've not been at N before
                        if (d.isDelayed() || maxValueIncl == null || d.le(maxValueIncl)) {
                            distanceToStartingPoint.put(n, distanceToN);
                            recursivelyComputeLinks(n, distanceToStartingPoint, maxValueIncl, followDelayed);
                        } else {
                            distanceToStartingPoint.put(n, DV.MAX_INT_DV); // beyond max value
                        }
                    } else {
                        DV newDistanceToN = min(distanceToN, currentDistanceToN);
                        distanceToStartingPoint.put(n, newDistanceToN);
                        if (newDistanceToN.lt(currentDistanceToN)) {
                            recursivelyComputeLinks(n, distanceToStartingPoint, maxValueIncl, followDelayed);
                        }
                    }
                } // else: ignore delayed links!
            });
        }
    }

    private DV min(DV d1, DV d2) {
        if (d1.equals(STATICALLY_ASSIGNED_DV) || d2.equals(STATICALLY_ASSIGNED_DV)) {
            return STATICALLY_ASSIGNED_DV;
        }
        return d1.min(d2);
    }

    private DV max(DV d1, DV d2) {
        return d1.max(d2);
    }

    @NotModified(contract = true)
    public void visit(@NotNull BiConsumer<Variable, Map<Variable, DV>> consumer) {
        nodeMap.values().forEach(n -> consumer.accept(n.variable, n.dependsOn));
    }

    @NotNull
    @Modified
    @Only(before = "frozen")
    private Node getOrCreate(@NotNull Variable v) {
        ensureNotFrozen();
        Objects.requireNonNull(v);
        Node node = nodeMap.get(v);
        if (node == null) {
            node = new Node(v);
            nodeMap.put(v, node);
        }
        return node;
    }

    @Only(before = "frozen")
    @Modified
    public void addNode(@NotNull Variable v, @NotNull Map<Variable, DV> dependsOn) {
        addNode(v, dependsOn, false, (o, n) -> o);
    }

    @Only(before = "frozen")
    @Modified
    public void addNode(@NotNull Variable v, @NotNull Map<Variable, DV> dependsOn, boolean bidirectional, BinaryOperator<DV> merger) {
        ensureNotFrozen();
        Node node = getOrCreate(v);
        for (Map.Entry<Variable, DV> e : dependsOn.entrySet()) {
            if (node.dependsOn == null) node.dependsOn = new TreeMap<>();
            node.dependsOn.merge(e.getKey(), e.getValue(), merger);
            if (bidirectional) {
                Node n = getOrCreate(e.getKey());
                if (n.dependsOn == null) n.dependsOn = new TreeMap<>();
                n.dependsOn.merge(v, e.getValue(), merger);
            }
        }
    }
}
