package org.e2immu.graph.op;

import org.e2immu.graph.G;
import org.e2immu.graph.V;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RemoveEdgesByVertexWeight<T> implements BreakCycles.ActionComputer<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GreedyEdgeRemoval.class);
    private final Map<V<T>, Long> vertexWeights;

    public RemoveEdgesByVertexWeight(Map<V<T>, Long> vertexWeights) {
        this.vertexWeights = vertexWeights;
    }

    @Override
    public BreakCycles.Action<T> compute(G<T> g, Cycle<T> cycle) {
        long lowestWeight = cycle.vertices().stream().mapToLong(vertexWeights::get).min().orElseThrow();
        Map<V<T>, Set<V<T>>> edgesToRemove = cycle.vertices().stream()
                .filter(v -> vertexWeights.get(v) == lowestWeight)
                .collect(Collectors.toUnmodifiableMap(v -> v, v -> g.edges(v).keySet(),
                        (s1, s2) -> Stream.concat(s1.stream(), s2.stream()).collect(Collectors.toUnmodifiableSet())));
        LOGGER.debug("Lowest weight {}, remove {}", lowestWeight, edgesToRemove);
        BreakCycles.EdgeRemoval2<T> info = new BreakCycles.EdgeRemoval2<>(edgesToRemove);
        G<T> subGraph = g.subGraph(cycle.vertices()).withFewerEdges(edgesToRemove);
        return new BreakCycles.Action<T>() {
            @Override
            public G<T> apply() {
                return subGraph;
            }

            @Override
            public BreakCycles.ActionInfo info() {
                return info;
            }
        };
    }
}
