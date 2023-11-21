package org.e2immu.analyser.util.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.LongBinaryOperator;
import java.util.stream.Collectors;

/*
Combination of grouping and breaking cycles.

 */
public class BreakCycles<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BreakCycles.class);

    public record Linearization<T>(List<Set<Set<T>>> list, List<ActionInfo> actionLog) {
        @Override
        public String toString() {
            return list.stream().map(s -> "["
                            + s.stream().map(set -> set.stream().map(Object::toString).sorted()
                                    .collect(Collectors.joining(", ")))
                            .sorted().collect(Collectors.joining(", "))
                            + "]")
                    .collect(Collectors.joining("; "));
        }
    }

    private record InternalLinearization<T>(List<Set<V<T>>> list, List<ActionInfo> actionLog) {

    }

    public interface ActionComputer<T> {
        Action<T> compute(G<T> g, Set<V<T>> cycle);
    }

    public interface Action<T> {
        G<T> apply();

        ActionInfo info();
    }

    public interface ActionInfo {

    }

    private final ActionComputer<T> actionComputer;

    public BreakCycles(ActionComputer<T> actionComputer) {
        this.actionComputer = actionComputer;
    }

    // list: sequential
    // set: parallel, these elements are independent (have no edges between them), can be "processed" in parallel
    // set: grouped, cycles cannot be broken here, must be processed together
    public Linearization<T> go(G<T> g) {
        InternalLinearization<T> linearization = go2(g, true);
        // unpack the vertices
        List<Set<Set<T>>> unpacked = linearization.list.stream()
                .map(s -> s.stream().map(V::ts).collect(Collectors.toUnmodifiableSet())).toList();
        return new Linearization<>(unpacked, linearization.actionLog);
    }

    // list: sequential
    // set: parallel
    // the vertex contains multiple Ts that are in a cycle which cannot or will not be broken
    private InternalLinearization<T> go2(G<T> g, boolean first) {
        GraphOperations.LinearizationResult<T> r = GraphOperations.linearize(g);
        if (r.quality() == 0) {
            return new InternalLinearization<>(r.linearized(), List.of());
        }
        List<Set<V<T>>> result = new ArrayList<>(r.linearized());
        /*
         we have at least one cycle, and some non-problematic nodes that can be added once the cycle has been linearized
         this must proceed recursively
         */
        assert !r.remainingCycles().isEmpty();
        List<List<Set<V<T>>>> newLinearizations = new ArrayList<>();
        List<ActionInfo> actionLog = new ArrayList<>();
        if (first) {
            LOGGER.debug("Have {} remaining cycles: {}", r.remainingCycles().size(),
                    r.remainingCycles().stream().map(c -> Integer.toString(c.size())).collect(Collectors.joining(",")));
        }
        for (Set<V<T>> cycle : r.remainingCycles()) {
            Action<T> action = actionComputer.compute(g, cycle);
            if (action == null) {
                // unbreakable cycle
                result.add(cycle);
            } else {
                // apply the action
                G<T> newG = action.apply();
                assert !newG.equals(g);
                InternalLinearization<T> internalLinearization = go2(newG, false);
                newLinearizations.add(internalLinearization.list);
                actionLog.add(action.info());
                actionLog.addAll(internalLinearization.actionLog);
            }
        }
        if (!newLinearizations.isEmpty()) {
            appendLinearizations(newLinearizations, result);
        }
        List<ActionInfo> immutableActionLog = List.copyOf(actionLog);
        if (r.nonProblematic().isEmpty()) {
            return new InternalLinearization<>(List.copyOf(result), immutableActionLog);
        }
        return new InternalLinearization<>(attachNonProblematicNodes(g, r.nonProblematic(), result), immutableActionLog);
    }

    private List<Set<V<T>>> attachNonProblematicNodes(G<T> g, List<V<T>> vs, List<Set<V<T>>> input) {
        Map<V<T>, Integer> positionOfVertex = new HashMap<>();
        List<Set<V<T>>> result = new ArrayList<>(input.size());
        int i = 0;
        for (Set<V<T>> set : input) {
            for (V<T> v : set) {
                positionOfVertex.put(v, i);
            }
            i++;
            result.add(new HashSet<>(set));
        }
        Set<V<T>> toDo = new HashSet<>(vs);
        while (!toDo.isEmpty()) {
            Set<V<T>> done = new HashSet<>();
            for (V<T> from : toDo) {
                Map<V<T>, Long> edges = g.edges(from);
                assert edges != null;
                int maxPosition = edges.keySet()
                        .stream().mapToInt(to -> positionOfVertex.getOrDefault(to, -1)).max().orElseThrow();
                Set<V<T>> toAdd;
                if (maxPosition == input.size() - 1) {
                    toAdd = new HashSet<>();
                    result.add(toAdd);
                } else if (maxPosition >= 0) {
                    toAdd = result.get(maxPosition + 1);
                } else {
                    toAdd = null;
                }
                if (toAdd != null) {
                    toAdd.add(from);
                    done.add(from);
                    positionOfVertex.put(from, maxPosition + 1);
                }
            }
            assert !done.isEmpty();
            toDo.removeAll(done);
        }
        result.replaceAll(Set::copyOf);
        return List.copyOf(result);
    }

    private void appendLinearizations(List<List<Set<V<T>>>> newLinearizations, List<Set<V<T>>> result) {
        if (newLinearizations.size() == 1) {
            result.addAll(newLinearizations.get(0));
            return;
        }
        int max = newLinearizations.stream().mapToInt(List::size).max().orElseThrow();
        for (int i = 0; i < max; i++) {
            Set<V<T>> set = new HashSet<>();
            for (List<Set<V<T>>> linearization : newLinearizations) {
                if (linearization.size() >= i + 1) {
                    Set<V<T>> s = linearization.get(i);
                    set.addAll(s);
                }
            }
            result.add(Set.copyOf(set));
        }
    }

    public record EdgeRemoval<T>(Map<V<T>, Map<V<T>, Long>> edges) implements ActionInfo {
    }


    private record VertexAndSomeEdges<T>(Map<V<T>, Map<V<T>, Long>> edges, long sum) {
    }

    public static <T> Iterator<Map<V<T>, Map<V<T>, Long>>> edgeIterator2(G<T> g,
                                                                         Comparator<Long> comparator,
                                                                         Long limit,
                                                                         LongBinaryOperator sumWeights) {
        List<VertexAndSomeEdges<T>> list = new LinkedList<>();
        for (Map.Entry<V<T>, Map<V<T>, Long>> entry : g.edges()) {
            Map<V<T>, Long> multiEdges = new HashMap<>();
            long sum = 0;
            for (Map.Entry<V<T>, Long> e2 : entry.getValue().entrySet()) {
                long weight = e2.getValue();
                if (weight < limit) {
                    multiEdges.put(e2.getKey(), weight);
                    sum = sumWeights.applyAsLong(sum, weight);
                    list.add(new VertexAndSomeEdges<>(Map.of(entry.getKey(), Map.of(e2.getKey(), weight)), weight));
                }
            }
            if (multiEdges.size() > 1) {
                list.add(new VertexAndSomeEdges<>(Map.of(entry.getKey(), multiEdges), sum));
            }
        }
        return list.stream().sorted((v1, v2) -> comparator.compare(v1.sum, v2.sum))
                .map(vase -> vase.edges)
                .iterator();
    }

    public static class GreedyEdgeRemoval<T> implements ActionComputer<T> {
        private final EdgePrinter<T> edgePrinter;
        private final Long limit;
        private final LongBinaryOperator sumWeights;

        public GreedyEdgeRemoval() {
            this(Object::toString, null, Long::sum);
        }

        public GreedyEdgeRemoval(EdgePrinter<T> edgePrinter, Long limit, LongBinaryOperator sumWeights) {
            this.edgePrinter = edgePrinter;
            this.limit = limit;
            this.sumWeights = sumWeights;
        }

        @Override
        public Action<T> compute(G<T> inputGraph, Set<V<T>> cycle) {
            G<T> g = inputGraph.subGraph(cycle);

            int bestQuality = cycle.size();
            assert bestQuality > 0;
            G<T> bestSubGraph = null;
            Map<V<T>, Map<V<T>, Long>> bestEdgesToRemove = null;

            Iterator<Map<V<T>, Map<V<T>, Long>>> iterator = edgeIterator2(g, Long::compareTo, limit, sumWeights);
            while (iterator.hasNext() && bestQuality > 0) {
                Map<V<T>, Map<V<T>, Long>> edgesToRemove = iterator.next();
                G<T> withoutEdges = g.withFewerEdgesMap(edgesToRemove);
                int quality = GraphOperations.qualityBasedOnTotalCluster(withoutEdges);
                if (quality < bestQuality) {
                    bestSubGraph = withoutEdges;
                    bestQuality = quality;
                    bestEdgesToRemove = edgesToRemove;
                }
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Best choice for greedy edge removal is {}, quality now {}",
                        edgePrinter.print(bestEdgesToRemove), bestQuality);
            }
            if (bestQuality < cycle.size()) {
                G<T> finalGraph = bestSubGraph;
                EdgeRemoval<T> info = new EdgeRemoval<>(bestEdgesToRemove);
                return new Action<T>() {
                    @Override
                    public G<T> apply() {
                        return finalGraph;
                    }

                    @Override
                    public ActionInfo info() {
                        return info;
                    }
                };
            }
            LOGGER.debug("No edge found that improves quality; keeping cycle of size {}", cycle.size());
            return null; // must be a group, we cannot break the cycle
        }
    }
}
