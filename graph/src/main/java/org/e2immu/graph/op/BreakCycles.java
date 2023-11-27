package org.e2immu.graph.op;

import org.e2immu.graph.G;
import org.e2immu.graph.V;
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

    // list: sequential dependence level
    // set: have the same dependence level
    public record Linearization<T>(List<Set<Cycle<T>>> list, List<ActionInfo> actionLog) {
        @Override
        public String toString() {
            return list.stream().map(s -> "["
                            + s.stream().map(cycle -> cycle.vertices().stream().map(Object::toString).sorted()
                                    .collect(Collectors.joining(", ")))
                            .sorted().collect(Collectors.joining(", "))
                            + "]")
                    .collect(Collectors.joining("; "));
        }

        public int maxCycleSize() {
            return list.stream().flatMap(Set::stream).mapToInt(Cycle::size).max().orElse(0);
        }
    }

    public interface ActionComputer<T> {
        Action<T> compute(G<T> g, Cycle<T> cycle);
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
        return go2(g, true);
    }

    // list: sequential
    // set: parallel
    // the vertex contains multiple Ts that are in a cycle which cannot or will not be broken
    private Linearization<T> go2(G<T> g, boolean first) {
        Linearize.Result<T> r = Linearize.linearize(g);
        Hierarchy<T> linearPart = r.linearized();
        List<Set<Cycle<T>>> linearPartTransformed = linearPart.list().stream()
                .map(set -> set.stream().map(v -> new Cycle<>(Set.of(v))).collect(Collectors.toUnmodifiableSet()))
                .toList();
        if (r.quality() == 0) {
            // no cycles here, so we replace each vertex with a single element set.
            return new Linearization<>(linearPartTransformed, List.of());
        }
        List<Set<Cycle<T>>> result = new ArrayList<>(linearPartTransformed);
        /*
         we have at least one cycle, and some non-problematic nodes that can be added once the cycle has been linearized
         this must proceed recursively
         */
        assert !r.remainingCycles().isEmpty();
        List<List<Set<Cycle<T>>>> newLinearizations = new ArrayList<>();
        List<ActionInfo> actionLog = new ArrayList<>();
        if (first) {
            LOGGER.info("Have {} remaining cycles: {}", r.remainingCycles().size(),
                    r.remainingCycles().cycles().stream().map(c -> Integer.toString(c.size()))
                            .collect(Collectors.joining(",")));
        }
        Set<Cycle<T>> cycles = new LinkedHashSet<>();
        for (Cycle<T> cycle : r.remainingCycles()) {
            LOGGER.info("Starting cycle of size {}", cycle.size());
            Action<T> action = actionComputer.compute(g, cycle);
            if (action == null) {
                // unbreakable cycle
                cycles.add(cycle);
            } else {
                // apply the action
                G<T> newG = action.apply();
                assert !newG.equals(g);
                Linearization<T> lin = go2(newG, false);
                newLinearizations.add(lin.list);
                actionLog.add(action.info());
                actionLog.addAll(lin.actionLog);
            }
        }
        if(!cycles.isEmpty()) {
            result.add(cycles);
        }
        if (!newLinearizations.isEmpty()) {
            appendLinearizations(newLinearizations, result);
        }
        List<ActionInfo> immutableActionLog = List.copyOf(actionLog);
        if (r.attachedToCycles().isEmpty()) {
            return new Linearization<>(List.copyOf(result), immutableActionLog);
        }
        List<Set<Cycle<T>>> sets = attachNonProblematicNodes(g, r.attachedToCycles(), result);
        return new Linearization<>(sets, immutableActionLog);
    }

    private List<Set<Cycle<T>>> attachNonProblematicNodes(G<T> g, Hierarchy<T> attachedToCycles, List<Set<Cycle<T>>> input) {
        Map<V<T>, Integer> positionOfVertex = new LinkedHashMap<>();
        List<Set<Cycle<T>>> result = new ArrayList<>(input.size());
        int i = 0;
        for (Set<Cycle<T>> set : input) {
            for (Cycle<T> set2 : set) {
                for (V<T> t : set2.vertices()) {
                    positionOfVertex.put(t, i);
                }
            }
            i++;
            result.add(new LinkedHashSet<>(set));
        }
        for (Set<V<T>> set : attachedToCycles.list()) {
            Set<Cycle<T>> newSet = new LinkedHashSet<>();
            for (V<T> from : set) {
                Map<V<T>, Long> edges = g.edges(from);
                assert edges != null;
                int maxPosition = edges.keySet()
                        .stream().mapToInt(to -> positionOfVertex.getOrDefault(to, -1)).max().orElseThrow();
                if (maxPosition == result.size() - 1) {
                    newSet.add(new Cycle<>(Set.of(from)));
                } else {
                    assert maxPosition >= 0;
                    Set<Cycle<T>> toAdd = result.get(maxPosition + 1);
                    toAdd.add(new Cycle<>(Set.of(from)));
                }
                positionOfVertex.put(from, maxPosition + 1);
            }
            if (!newSet.isEmpty()) {
                result.add(newSet);
            }
        }
        result.replaceAll(Set::copyOf);
        return List.copyOf(result);
    }

    private void appendLinearizations(List<List<Set<Cycle<T>>>> newLinearizations, List<Set<Cycle<T>>> result) {
        if (newLinearizations.size() == 1) {
            result.addAll(newLinearizations.get(0));
            return;
        }
        int max = newLinearizations.stream().mapToInt(List::size).max().orElseThrow();
        for (int i = 0; i < max; i++) {
            Set<Cycle<T>> set = new LinkedHashSet<>();
            for (List<Set<Cycle<T>>> linearization : newLinearizations) {
                if (linearization.size() >= i + 1) {
                    Set<Cycle<T>> s = linearization.get(i);
                    set.addAll(s);
                }
            }
            result.add(set);
        }
    }

    public record EdgeRemoval<T>(Map<V<T>, Map<V<T>, Long>> edges) implements ActionInfo {
    }

    public record EdgeRemoval2<T>(Map<V<T>, Set<V<T>>> edges) implements ActionInfo {
    }

    private record VertexAndSomeEdges<T>(Map<V<T>, Map<V<T>, Long>> edges, long sum) {
    }

    public static <T> Iterator<Map<V<T>, Map<V<T>, Long>>> edgeIterator2(G<T> g,
                                                                         Comparator<Long> comparator,
                                                                         Long limit,
                                                                         LongBinaryOperator sumWeights) {
        List<VertexAndSomeEdges<T>> list = new LinkedList<>();
        for (Map.Entry<V<T>, Map<V<T>, Long>> entry : g.edges()) {
            Map<V<T>, Long> multiEdges = new LinkedHashMap<>();
            long sum = 0;
            for (Map.Entry<V<T>, Long> e2 : entry.getValue().entrySet()) {
                long weight = e2.getValue();
                if (limit == null || weight < limit) {
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

}
