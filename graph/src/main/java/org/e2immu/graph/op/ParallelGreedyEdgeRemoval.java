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

        int edgeCount();
    }

    public static class StoppableEdgeBlockStreamGenerator<T> implements EdgeBlockStreamGenerator<T> {
        private final List<Map<V<T>, Map<V<T>, Long>>> edges;
        private final int blockSize;
        private final int edgeCount;
        private final int blocks;

        public StoppableEdgeBlockStreamGenerator(G<T> g, EdgeIterator<T> edgeIterator, int blockSize) {
            Iterator<Map<V<T>, Map<V<T>, Long>>> iterator = edgeIterator.iterator(g);
            edges = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false).toList();
            edgeCount = edges.size();
            this.blockSize = blockSize;
            this.blocks = edges.size() / blockSize;
            LOGGER.info("Have {} edges, block size {}, expect {} blocks", edgeCount, blockSize, blocks);
        }

        @Override
        public Stream<List<Map<V<T>, Map<V<T>, Long>>>> stream() {
            Iterator<List<Map<V<T>, Map<V<T>, Long>>>> iterator = new MyIterator();

            return StreamSupport.stream(Spliterators.spliterator(iterator, blocks, Spliterator.CONCURRENT), true);
        }

        @Override
        public int edgeCount() {
            return edgeCount;
        }

        class MyIterator implements Iterator<List<Map<V<T>, Map<V<T>, Long>>>> {
            int pos;

            @Override
            public boolean hasNext() {
                return pos < edgeCount;
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

    public ParallelGreedyEdgeRemoval(EdgePrinter<T> edgePrinter, EdgeIterator<T> iterator, TimedLogger timedLogger) {
        this.edgePrinter = edgePrinter;
        this.edgeIterator = iterator;
        this.timedLogger = timedLogger;
    }

    private record Best<T>(G<T> subGraph, int quality, Map<V<T>, Map<V<T>, Long>> edgesToRemove) {
    }

    private static <T> Best<T> compute(G<T> g, List<Map<V<T>, Map<V<T>, Long>>> block) {
        int bestQuality = Integer.MAX_VALUE;
        G<T> bestSubGraph = null;
        Map<V<T>, Map<V<T>, Long>> bestEdgesToRemove = null;
        for (Map<V<T>, Map<V<T>, Long>> edgesToRemove : block) {
            G<T> withoutEdges = g.withFewerEdgesMap(edgesToRemove);
            int quality = Linearize.qualityBasedOnTotalCluster(withoutEdges);
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
    public BreakCycles.Action<T> compute(G<T> inputGraph, Cycle<T> cycle) {
        G<T> g = inputGraph.subGraph(cycle.vertices());
        double cycleSize = cycle.size();
        EdgeBlockStreamGenerator<T> generator = new StoppableEdgeBlockStreamGenerator<>(g, edgeIterator, 50);
        AtomicInteger counter = new AtomicInteger();
        AtomicInteger bestQuality = new AtomicInteger(Integer.MAX_VALUE);
        Best<T> overallBest = generator.stream().map(block -> {
            if (block.isEmpty()) {
                return new Best<T>(null, Integer.MAX_VALUE, Map.of());
            }
            Best<T> best = compute(g, block);
            if (best.quality < bestQuality.get()) {
                bestQuality.set(best.quality);
            }
            int count = counter.addAndGet(block.size());
            timedLogger.info("Count {}, best {}", count, bestQuality);
            return best;
        }).min(Comparator.comparing(Best::quality)).orElse(null);
        if (overallBest != null) {
            assert counter.get() == generator.edgeCount();
            LOGGER.info("Best choice for greedy edge removal is {}, quality now {}",
                    edgePrinter.print(overallBest.edgesToRemove), overallBest.quality);
            if (overallBest.quality < cycle.size()) {
                BreakCycles.EdgeRemoval<T> info = new BreakCycles.EdgeRemoval<>(overallBest.edgesToRemove);
                return new BreakCycles.Action<>() {
                    @Override
                    public G<T> apply() {
                        return overallBest.subGraph.subGraph(cycle.vertices());
                    }

                    @Override
                    public BreakCycles.ActionInfo info() {
                        return info;
                    }
                };
            }
        }
        LOGGER.info("No edge found that improves quality; keeping cycle of size {}", cycle.size());
        return null; // must be a group, we cannot break the cycle
    }
}
