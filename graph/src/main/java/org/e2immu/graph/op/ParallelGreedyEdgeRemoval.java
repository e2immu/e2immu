package org.e2immu.graph.op;

import org.e2immu.graph.*;
import org.e2immu.graph.util.TimedLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ParallelGreedyEdgeRemoval<T> implements BreakCycles.ActionComputer<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelGreedyEdgeRemoval.class);

    public interface EdgeBlockStreamGenerator<T> {
        Stream<List<Map<V<T>, Map<V<T>, Long>>>> stream();

        void stop();
    }

    public static class StoppableEdgeBlockStreamGenerator<T> implements EdgeBlockStreamGenerator<T> {
        private final List<Map<V<T>, Map<V<T>, Long>>> edges;
        private final int blockSize;
        private final int vertexCount;
        private boolean stop;

        public StoppableEdgeBlockStreamGenerator(G<T> g, EdgeIterator<T> edgeIterator, int blockSize) {
            Iterator<Map<V<T>, Map<V<T>, Long>>> iterator = edgeIterator.iterator(g);
            edges = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false).toList();
            this.blockSize = blockSize;
            this.vertexCount = g.vertices().size();
        }

        @Override
        public Stream<List<Map<V<T>, Map<V<T>, Long>>>> stream() {
            Iterator<List<Map<V<T>, Map<V<T>, Long>>>> iterator = new MyIterator();
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
        }

        @Override
        public void stop() {
            this.stop = true;
        }

        class MyIterator implements Iterator<List<Map<V<T>, Map<V<T>, Long>>>> {
            int pos;

            @Override
            public boolean hasNext() {
                return !stop && pos < vertexCount;
            }

            @Override
            public List<Map<V<T>, Map<V<T>, Long>>> next() {
                List<Map<V<T>, Map<V<T>, Long>>> list = edges.subList(pos, Math.min(pos + blockSize, edges.size()));
                pos += blockSize;
                return list;
            }
        }
    }

    private final EdgePrinter<T> edgePrinter;
    private final EdgeIterator<T> edgeIterator;
    private final TimedLogger timedLogger;
    private final double improvement;

    public ParallelGreedyEdgeRemoval(EdgePrinter<T> edgePrinter, EdgeIterator<T> iterator, TimedLogger timedLogger,
                                     double improvement) {
        this.edgePrinter = edgePrinter;
        this.edgeIterator = iterator;
        this.timedLogger = timedLogger;
        this.improvement = improvement;
    }

    private record Best<T>(G<T> subGraph, int quality, Map<V<T>, Map<V<T>, Long>> edgesToRemove) {
    }

    private static <T> Best<T> compute(G<T> g, List<Map<V<T>, Map<V<T>, Long>>> block) {
        int bestQuality = Integer.MAX_VALUE;
        G<T> bestSubGraph = null;
        Map<V<T>, Map<V<T>, Long>> bestEdgesToRemove = null;
        for (Map<V<T>, Map<V<T>, Long>> edgesToRemove : block) {
            G<T> withoutEdges = g.withFewerEdgesMap(edgesToRemove);
            int quality = Linearization.qualityBasedOnTotalCluster(withoutEdges);
            if (quality < bestQuality) {
                bestSubGraph = withoutEdges;
                bestQuality = quality;
                bestEdgesToRemove = edgesToRemove;
            }
        }
        assert bestSubGraph != null;
        return new Best<>(bestSubGraph, bestQuality, bestEdgesToRemove);
    }

    @Override
    public BreakCycles.Action<T> compute(G<T> inputGraph, Set<V<T>> cycle) {
        G<T> g = inputGraph.subGraph(cycle);
        double cycleSize = cycle.size();
        EdgeBlockStreamGenerator<T> generator = new StoppableEdgeBlockStreamGenerator<>(g, edgeIterator, 100);
        AtomicInteger counter = new AtomicInteger();
        AtomicInteger bestQuality = new AtomicInteger(Integer.MAX_VALUE);
        Best<T> overallBest = generator.stream().map(block -> {
            Best<T> best = compute(g, block);
            double percentageQuality = best.quality / cycleSize;
            if (percentageQuality < improvement) {
                LOGGER.info("Stop, have improvement of {} percent", percentageQuality * 100);
            }
            int count = counter.addAndGet(block.size());
            if (best.quality < bestQuality.get()) {
                bestQuality.set(best.quality);
            }
            timedLogger.info("Count {}, best {}", count, bestQuality);
            return best;
        }).min(Comparator.comparing(Best::quality)).orElseThrow();
        LOGGER.info("Best choice for greedy edge removal is {}, quality now {}",
                edgePrinter.print(overallBest.edgesToRemove), overallBest.quality);
        if (overallBest.quality < cycle.size()) {
            BreakCycles.EdgeRemoval<T> info = new BreakCycles.EdgeRemoval<>(overallBest.edgesToRemove);
            return new BreakCycles.Action<T>() {
                @Override
                public G<T> apply() {
                    return overallBest.subGraph;
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
