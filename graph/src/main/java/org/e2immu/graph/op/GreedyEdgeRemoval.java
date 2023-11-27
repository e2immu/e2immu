package org.e2immu.graph.op;

import org.e2immu.graph.EdgeIterator;
import org.e2immu.graph.EdgePrinter;
import org.e2immu.graph.G;
import org.e2immu.graph.V;
import org.e2immu.graph.util.TimedLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class GreedyEdgeRemoval<T> implements BreakCycles.ActionComputer<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GreedyEdgeRemoval.class);

    private final EdgePrinter<T> edgePrinter;
    private final EdgeIterator<T> edgeIterator;
    private final TimedLogger timedLogger;

    public GreedyEdgeRemoval() {
        this(Object::toString, g -> g.edgeIterator(Long::compareTo, null), null);
    }

    public GreedyEdgeRemoval(EdgePrinter<T> edgePrinter, EdgeIterator<T> edgeIterator, TimedLogger timedLogger) {
        this.edgePrinter = edgePrinter;
        this.edgeIterator = edgeIterator;
        this.timedLogger = timedLogger;
    }

    @Override
    public BreakCycles.Action<T> compute(G<T> inputGraph, Cycle<T> cycle) {
        G<T> g = inputGraph.subGraph(cycle.vertices());

        int bestQuality = cycle.size();
        assert bestQuality > 0;
        G<T> bestSubGraph = null;
        Map<V<T>, Map<V<T>, Long>> bestEdgesToRemove = null;
        int count = 0;
        Iterator<Map<V<T>, Map<V<T>, Long>>> iterator = edgeIterator.iterator(g);
        while (iterator.hasNext() && bestQuality > 0) {
            Map<V<T>, Map<V<T>, Long>> edgesToRemove = iterator.next();
            G<T> withoutEdges = g.withFewerEdgesMap(edgesToRemove);
            int quality = Linearize.qualityBasedOnTotalCluster(withoutEdges);
            if (quality < bestQuality) {
                bestSubGraph = withoutEdges;
                bestQuality = quality;
                bestEdgesToRemove = edgesToRemove;
            }
            ++count;
            if (timedLogger != null) {
                timedLogger.info("Edge removal, done {}, best {}", count, bestQuality);
            }
        }
        LOGGER.info("Best choice for greedy edge removal is {}, quality now {}",
                edgePrinter.print(bestEdgesToRemove), bestQuality);
        if (bestQuality < cycle.size()) {
            G<T> finalGraph = bestSubGraph.subGraph(cycle.vertices());
            BreakCycles.EdgeRemoval<T> info = new BreakCycles.EdgeRemoval<>(bestEdgesToRemove);
            return new BreakCycles.Action<T>() {
                @Override
                public G<T> apply() {
                    return finalGraph;
                }

                @Override
                public BreakCycles.ActionInfo info() {
                    return info;
                }
            };
        }
        LOGGER.info("No edge found that improves quality; keeping cycle of size {}", cycle.size());
        return null; // must be a group, we cannot break the cycle
    }
}
