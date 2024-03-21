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

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.graph.op.DijkstraShortestPath;
import org.e2immu.support.Freezable;
import org.jgrapht.alg.util.UnionFind;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.LV.*;


public class WeightedGraphImpl extends Freezable implements WeightedGraph {

    private final Map<Variable, Node> nodeMap;
    private final Cache cache;

    // for testing only!
    public WeightedGraphImpl() {
        this(new GraphCacheImpl(10));
    }

    public WeightedGraphImpl(Cache cache) {
        nodeMap = new LinkedHashMap<>();
        this.cache = cache;
    }

    private static class Node {
        Map<Variable, LV> dependsOn;
        final Variable variable;

        private Node(Variable v) {
            variable = v;
        }
    }

    /*
    https://en.wikipedia.org/wiki/Disjoint-set_data_structure
    loop over all vertices and edges, and use a disjoint-set data structure to efficiently group or cluster all
    nodes linked by STATICALLY_ASSIGNED edges.

    The return-value cluster is a special case, it overlaps with the other ones.
     */
    @Override
    public ClusterResult staticClusters() {
        super.freeze();
        UnionFind<Variable> unionFind = new UnionFind<>(nodeMap.keySet());
        Variable rv = null;
        Set<Variable> dependsOnRv = null;
        for (Map.Entry<Variable, Node> entry : nodeMap.entrySet()) {
            Variable variable = entry.getKey();
            boolean isRv = variable instanceof ReturnVariable;
            if (isRv) {
                rv = variable;
                dependsOnRv = new HashSet<>();
            }
            Map<Variable, LV> dependsOn = entry.getValue().dependsOn;
            if (dependsOn != null) {
                for (Map.Entry<Variable, LV> e2 : dependsOn.entrySet()) {
                    if (LINK_STATICALLY_ASSIGNED.equals(e2.getValue())) {
                        if (isRv) {
                            dependsOnRv.add(e2.getKey());
                        } else {
                            unionFind.union(variable, e2.getKey());
                        }
                    }
                }
            }
        }
        Map<Variable, Cluster> representativeToCluster = new LinkedHashMap<>();
        for (Variable variable : nodeMap.keySet()) {
            if (!(variable instanceof ReturnVariable)) {
                Variable representative = unionFind.find(variable);
                Cluster cluster = representativeToCluster.computeIfAbsent(representative,
                        v -> new Cluster(new LinkedHashSet<>()));
                cluster.variables().add(variable);
            }
        }
        List<Cluster> clusters = representativeToCluster.values().stream().toList();
        Cluster rvCluster;
        if (rv != null) {
            rvCluster = new Cluster(new LinkedHashSet<>());
            rvCluster.variables().add(rv);
            for (Variable v : dependsOnRv) {
                Variable r = unionFind.find(v);
                Cluster c = representativeToCluster.get(r);
                rvCluster.variables().addAll(c.variables());
            }
        } else {
            rvCluster = null;
        }
        return new ClusterResult(rvCluster, rv, clusters);
    }

    public int size() {
        return nodeMap.size();
    }

    public boolean isEmpty() {
        return nodeMap.isEmpty();
    }

    public void visit(BiConsumer<Variable, Map<Variable, LV>> consumer) {
        nodeMap.values().forEach(n -> consumer.accept(n.variable, n.dependsOn));
    }

    private Node getOrCreate(Variable v) {
        ensureNotFrozen();
        Objects.requireNonNull(v);
        Node node = nodeMap.get(v);
        if (node == null) {
            node = new Node(v);
            nodeMap.put(v, node);
        }
        return node;
    }


    @SuppressWarnings("unchecked")
    public void addNode(Variable v, Map<Variable, LV> dependsOn) {
        ensureNotFrozen();
        Node node = getOrCreate(v);
        for (Map.Entry<Variable, LV> e : dependsOn.entrySet()) {
            if (node.dependsOn == null) {
                node.dependsOn = new LinkedHashMap<>();
            }
            LV linkLevel = e.getValue();
            assert !LINK_INDEPENDENT.equals(linkLevel);

            /*
            ASSIGNED links are symmetrical, except for links involving the return variable.
            All other links can be asymmetrical.
             */
            LV min = node.dependsOn.merge(e.getKey(), linkLevel, LV::min);
            if ((LINK_STATICALLY_ASSIGNED.equals(min) || LINK_ASSIGNED.equals(min))
                && !(e.getKey() instanceof ReturnVariable)
                && !(v instanceof ReturnVariable)) {
                Node n = getOrCreate(e.getKey());
                if (n.dependsOn == null) {
                    n.dependsOn = new LinkedHashMap<>();
                }
                n.dependsOn.merge(v, min, LV::min);
            }
        }
    }

    static Comparator<String> REVERSE_STRING_COMPARATOR = (s1, s2) -> {
        int i1 = s1.length() - 1;
        int i2 = s2.length() - 1;
        while (i1 >= 0 && i2 >= 0) {
            int c = Character.compare(s1.charAt(i1), s2.charAt(i2));
            if (c != 0) return c;
            --i1;
            --i2;
        }
        if (i1 >= 0) return 1;
        if (i2 >= 0) return -1;
        return 0;
    };

    static Comparator<Variable> REVERSE_FQN_COMPARATOR = (v1, v2) ->
            REVERSE_STRING_COMPARATOR.compare(v1.fullyQualifiedName(), v2.fullyQualifiedName());

    @SuppressWarnings("unchecked")
    @Override
    public ShortestPath shortestPath() {
        int n = nodeMap.size();
        Variable[] variables = new Variable[n];
        // -- CACHE --
        int j = 0;
        for (Variable v : nodeMap.keySet()) {
            variables[j++] = v;
        }
        // we need a stable order across the variables; given the huge prefixes of parameters and fields,
        // it seems a lot faster to sort starting from the back.
        Arrays.sort(variables, REVERSE_FQN_COMPARATOR); // default: by name
        // -- CACHE --
        Map<Variable, Integer> variableIndex = new LinkedHashMap<>();
        int i = 0;
        for (Variable v : variables) {
            variableIndex.put(v, i);
            ++i;
        }
        StringBuilder sb = new StringBuilder(n * n * 5);
        Map<Integer, Map<Integer, DijkstraShortestPath.DC>> edges = new LinkedHashMap<>();
        Map<Integer, Map<Integer, DijkstraShortestPath.DC>> edgesHigh = new LinkedHashMap<>();
        CausesOfDelay delay = null;
        for (int d1 = 0; d1 < n; d1++) {
            Node node = nodeMap.get(variables[d1]);
            Map<Variable, LV> dependsOn = node.dependsOn;
            sb.append(d1);
            if (dependsOn != null && !dependsOn.isEmpty()) {

                Map<Integer, DijkstraShortestPath.DC> edgesOfD1 = new LinkedHashMap<>();
                edges.put(d1, edgesOfD1);
                Map<Integer, DijkstraShortestPath.DC> edgesOfD1High = new LinkedHashMap<>();
                edgesHigh.put(d1, edgesOfD1High);
                List<String> unsorted = new ArrayList<>(dependsOn.size());
                for (Map.Entry<Variable, LV> e2 : dependsOn.entrySet()) {
                    int d2 = variableIndex.get(e2.getKey());

                    LV dv = e2.getValue();
                    if (dv.isDelayed() && delay == null) {
                        delay = dv.causesOfDelay();
                    }
                    HiddenContent hc;
                    if (e2.getValue().isCommonHC()) {
                        hc = e2.getValue().theirs();
                    } else {
                        hc = null;
                    }
                    long d = ShortestPathImpl.toDistanceComponent(dv);
                    edgesOfD1.put(d2, new DijkstraShortestPath.DC(d, hc));
                    long dHigh = ShortestPathImpl.toDistanceComponentHigh(dv);
                    edgesOfD1High.put(d2, new DijkstraShortestPath.DC(dHigh, hc));

                    unsorted.add(d2 + ":" + ShortestPathImpl.code(dv));
                }
                sb.append("(");
                sb.append(unsorted.stream().sorted().collect(Collectors.joining(";")));
                sb.append(")");
            } else {
                sb.append("*");
            }
        }
        Cache.Hash hash = cache.createHash(sb.toString());
        ShortestPathImpl.LinkMap linkMap = (ShortestPathImpl.LinkMap)
                cache.computeIfAbsent(hash, h -> new ShortestPathImpl.LinkMap(new LinkedHashMap<>(), new AtomicInteger()));
        return new ShortestPathImpl(variableIndex, variables, edges, edgesHigh, delay, linkMap);
    }

    @Override
    public LV edgeValueOrNull(Variable v1, Variable v2) {
        Node n = nodeMap.get(v1);
        if (n != null && n.dependsOn != null) {
            return n.dependsOn.get(v2);
        }
        return null;
    }
}
