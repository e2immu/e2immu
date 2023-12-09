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
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.annotation.*;
import org.e2immu.annotation.eventual.Only;
import org.e2immu.support.Freezable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;

import static org.e2immu.analyser.analyser.LinkedVariables.*;
import static org.e2immu.analyser.analyser.LinkedVariables.LINK_STATICALLY_ASSIGNED;

@ImmutableContainer(after = "frozen", hc = true)
public class WeightedGraphImpl extends Freezable implements WeightedGraph {

    @SuppressWarnings("unchecked")
    public WeightedGraphImpl(Supplier<Map<?, ?>> mapSupplier) {
        this.mapSupplier = mapSupplier;
        nodeMap = (Map<Variable, Node>) mapSupplier.get();
    }

    /**
     * In-house implementation of a directed graph that is used to model the links between objects.
     * A distance of 0 (STATICALLY_ASSIGNED) is always kept, even across delays.
     * <p>
     * Hidden content: Variable, DV are interfaces with different implementations.
     */
    private static class Node {
        Map<Variable, DV> dependsOn;
        final Variable variable;

        private Node(Variable v) {
            variable = v;
        }
    }

    private final Supplier<Map<?, ?>> mapSupplier;
    @Modified
    private final Map<Variable, Node> nodeMap;

    @NotModified
    public int size() {
        return nodeMap.size();
    }

    @NotModified
    public boolean isEmpty() {
        return nodeMap.isEmpty();
    }

    @NotModified(contract = true)
    public void visit(@NotNull @Independent(hc = true) BiConsumer<Variable, Map<Variable, DV>> consumer) {
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
    public void addNode(@NotNull @Independent(hc = true) Variable v,
                        @NotNull @Independent(hc = true) Map<Variable, DV> dependsOn) {
        addNode(v, dependsOn, false, (o, n) -> o);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addNode(Variable v, Object... variableDvPairs) {
        Map<Variable, DV> dependsOn = (Map<Variable, DV>) mapSupplier.get();
        for (int i = 0; i < variableDvPairs.length; i += 2) {
            dependsOn.put((Variable) variableDvPairs[i], (DV) variableDvPairs[i + 1]);
        }
        addNode(v, dependsOn, false, (o, n) -> o);
    }

    @SuppressWarnings("unchecked")
    @Only(before = "frozen")
    @Modified
    public void addNode(@NotNull @Independent(hc = true) Variable v,
                        @NotNull @Independent(hc = true) Map<Variable, DV> dependsOn,
                        boolean bidirectional,
                        BinaryOperator<DV> merger) {
        ensureNotFrozen();
        Node node = getOrCreate(v);
        for (Map.Entry<Variable, DV> e : dependsOn.entrySet()) {
            if (node.dependsOn == null) {
                node.dependsOn = (Map<Variable, DV>) mapSupplier.get();
            }
            DV linkLevel = e.getValue();
            assert !LinkedVariables.LINK_INDEPENDENT.equals(linkLevel);

            node.dependsOn.merge(e.getKey(), linkLevel, merger);
            if (bidirectional || LinkedVariables.LINK_STATICALLY_ASSIGNED.equals(linkLevel)
                    && !(e.getKey() instanceof This)
                    && !(v instanceof This)
                    && !(e.getKey() instanceof ReturnVariable)
                    && !(v instanceof ReturnVariable)) {
                Node n = getOrCreate(e.getKey());
                if (n.dependsOn == null) {
                    n.dependsOn = (Map<Variable, DV>) mapSupplier.get();
                }
                n.dependsOn.merge(v, linkLevel, merger);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public ShortestPath shortestPath() {
        int n = nodeMap.size();
        Variable[] variables = new Variable[n];
        Map<Variable, Integer> variableIndex = (Map<Variable, Integer>) mapSupplier.get();
        int i = 0;
        for (Variable v : nodeMap.keySet()) {
            variableIndex.put(v, i);
            variables[i] = v;
            ++i;
        }
        DV[][] distances = new DV[n][n];
        for (Map.Entry<Variable, Node> entry : nodeMap.entrySet()) {
            Map<Variable, DV> dependsOn = entry.getValue().dependsOn;
            if (dependsOn != null) {
                int d1 = variableIndex.get(entry.getKey());
                for (Map.Entry<Variable, DV> e2 : dependsOn.entrySet()) {
                    int d2 = variableIndex.get(e2.getKey());
                    distances[d1][d2] = e2.getValue();
                }
            }
        }
        return new ShortestPathImpl(variableIndex, variables, distances);
    }
}
