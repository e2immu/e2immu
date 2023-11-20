package org.e2immu.analyser.util.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/*
Combination of grouping and breaking cycles.

 */
public class BreakCycles<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BreakCycles.class);

    public record Linearization<T>(List<Set<Set<T>>> list) {
        @Override
        public String toString() {
            return list.stream().map(s -> "["
                            + s.stream().map(set -> set.stream().map(Object::toString).sorted().collect(Collectors.joining(", ")))
                            .sorted().collect(Collectors.joining(", "))
                            + "]")
                    .collect(Collectors.joining("; "));
        }
    }

    public interface ActionComputer<T> {
        Action<T> compute(G<T> g, Set<V<T>> cycle);
    }

    public interface Action<T> {
        G<T> apply();
    }

    private final ActionComputer<T> actionComputer;

    public BreakCycles(ActionComputer<T> actionComputer) {
        this.actionComputer = actionComputer;
    }

    // list: sequential
    // set: parallel, these elements are independent (have no edges between them), can be "processed" in parallel
    // set: grouped, cycles cannot be broken here, must be processed together
    public Linearization<T> go(G<T> g) {
        List<Set<V<T>>> list = go2(g);
        // unpack the vertices
        List<Set<Set<T>>> unpacked = list.stream()
                .map(s -> s.stream().map(V::ts).collect(Collectors.toUnmodifiableSet())).toList();
        return new Linearization<>(unpacked);
    }

    // list: sequential
    // set: parallel
    // the vertex contains multiple Ts that are in a cycle which cannot or will not be broken
    private List<Set<V<T>>> go2(G<T> g) {
        GraphOperations.LinearizationResult<T> r = GraphOperations.linearize(g);
        if (r.quality() == 0) {
            return r.linearized();
        }
        List<Set<V<T>>> result = new ArrayList<>(r.linearized());
            /*
             we have at least one cycle, and some non-problematic nodes that can be added once the cycle has been linearized
             this must proceed recursively
             */
        assert !r.remainingCycles().isEmpty();
        List<List<Set<V<T>>>> newLinearizations = new ArrayList<>();
        for (Set<V<T>> cycle : r.remainingCycles()) {
            Action<T> action = actionComputer.compute(g, cycle);
            if (action == null) {
                // unbreakable cycle
                result.add(cycle);
            } else {
                // apply the action
                G<T> newG = action.apply();
                assert !newG.equals(g);
                List<Set<V<T>>> newLinearization = go2(newG);
                newLinearizations.add(newLinearization);
            }
        }
        appendLinearizations(newLinearizations, result);
        if (r.nonProblematic().isEmpty()) {
            return List.copyOf(result);
        }
        return attachNonProblematicNodes(g, r.nonProblematic(), result);
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

    public static class GreedyEdgeRemoval<T> implements ActionComputer<T> {

        @Override
        public Action<T> compute(G<T> inputGraph, Set<V<T>> cycle) {
            G<T> g = inputGraph.subGraph(cycle);
            int n = 1;
            int bestQuality = cycle.size();
            assert bestQuality > 0;
            G<T> bestSubGraph = null;
            Map<V<T>, Map<V<T>, Long>> bestEdgesToRemove = null;
            boolean found = false;
            while (n < cycle.size() && !found) {
                Iterator<Map<V<T>, Map<V<T>, Long>>> iterator = g.edgeIterator(n, Long::compareTo);
                while (iterator.hasNext() && bestQuality > 0) {
                    Map<V<T>, Map<V<T>, Long>> edgesToRemove = iterator.next();
                    G<T> withoutEdges = g.withFewerEdgesMap(edgesToRemove);
                    int quality = GraphOperations.linearize(withoutEdges).quality();
                    if (quality < bestQuality) {
                        bestSubGraph = withoutEdges;
                        bestQuality = quality;
                        bestEdgesToRemove = edgesToRemove;
                        found = true; // don't go to n+1
                    }
                    if (bestQuality == 0) {
                        break; // stop, optimal solution
                    }
                }
                ++n;
            }
            LOGGER.debug("Best choice for greedy edge removal is {}, quality now {}", bestEdgesToRemove, bestQuality);
            if (bestQuality < cycle.size()) {
                G<T> finalGraph = bestSubGraph;
                return new Action<T>() {
                    @Override
                    public G<T> apply() {
                        // FIXME and now the recursion
                        return finalGraph;
                    }
                };
            }
            return null; // must be a group, we cannot break the cycle
        }
    }
}
