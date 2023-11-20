package org.e2immu.analyser.util.graph;

import java.util.*;
import java.util.stream.Collectors;

public class GraphOperations {

    public record LinearizationResult<T>(List<Set<V<T>>> linearized,
                                         List<V<T>> nonProblematic,
                                         List<Set<V<T>>> remainingCycles) {

        /*
        The quality of an operation is measured by the size of the largest cycle after linearization.
         */
        public int quality() {
            return remainingCycles.stream().mapToInt(Set::size).max().orElse(0);
        }

        @Override
        public String toString() {
            return "L=" + linearized.stream()
                    .map(s -> "[" + s.stream().map(V::toString).sorted().collect(Collectors.joining(", ")) + "]")
                    .collect(Collectors.joining("; "))
                    + " P=" + nonProblematic.stream().map(V::toString).sorted().collect(Collectors.joining(", "))
                    + " R=" + remainingCycles.stream().map(s -> "[" +
                            s.stream().map(V::toString).sorted().collect(Collectors.joining(", ")) + "]")
                    .collect(Collectors.joining("; "));
        }
    }

    public static <T> LinearizationResult<T> linearize(G<T> g) {
        return linearize(g, false);
    }

    public static <T> LinearizationResult<T> linearize(G<T> g, boolean onlyLinear) {
        Set<V<T>> toDo = new HashSet<>(g.vertices());
        Set<V<T>> done = new HashSet<>();
        List<Set<V<T>>> linearResult = new ArrayList<>();
        List<Set<V<T>>> cycles = new ArrayList<>();
        List<V<T>> attachedToCycle = new ArrayList<>();
        while (!toDo.isEmpty()) {
            Set<V<T>> localLinear = new HashSet<>();
            for (V<T> v : toDo) {
                Map<V<T>, Long> edgeMap = g.edges(v);
                Set<V<T>> edges = edgeMap == null ? null : edgeMap.keySet();
                boolean safe;
                if (edges == null || edges.isEmpty()) {
                    safe = true;
                } else {
                    Set<V<T>> copy = new HashSet<>(edges);
                    copy.removeAll(done);
                    copy.remove(v);
                    safe = copy.isEmpty();
                }
                if (safe) {
                    localLinear.add(v);
                }
            }
            if (localLinear.isEmpty()) {
                if (!onlyLinear) {
                    // the remaining vertices form one or more cycles; but they can still be pruned a bit
                    attachedToCycle.addAll(removeAsManyAsPossible(g, toDo));
                    attachedToCycle.forEach(t -> {
                        toDo.remove(t);
                        done.add(t);
                    });
                    // find the connected cycles in the remaining vertices
                    G<T> subGraph = g.subGraph(toDo);
                    cycles.addAll(findConnectedSubSets(subGraph, toDo));
                }
                break;
            } else {
                done.addAll(localLinear);
                localLinear.forEach(toDo::remove);
                linearResult.add(Set.copyOf(localLinear));
            }
        }
        return new LinearizationResult<>(List.copyOf(linearResult), List.copyOf(attachedToCycle),
                List.copyOf(cycles));
    }

    private static <T> List<V<T>> removeAsManyAsPossible(G<T> g, Set<V<T>> toDo) {
        G<T> reverseSub = g.reverseSubGraph(toDo);
        LinearizationResult<T> r = linearize(reverseSub, true);
        return r.linearized.stream().flatMap(Collection::stream).toList();
    }

    public static <T> List<Set<V<T>>> findConnectedSubSets(G<T> g, Set<V<T>> startingPoints) {
        List<Set<V<T>>> result = new ArrayList<>();
        Set<V<T>> toDo = new HashSet<>(startingPoints);
        while (!toDo.isEmpty()) {
            V<T> v = toDo.stream().findFirst().orElseThrow();
            Set<V<T>> connected = follow(g, v);
            result.add(Set.copyOf(connected));
            toDo.removeAll(connected);
        }
        return List.copyOf(result);
    }

    private static <T> Set<V<T>> follow(G<T> g, V<T> startingPoint) {
        List<V<T>> toDo = new LinkedList<>();
        Set<V<T>> connected = new HashSet<>();
        toDo.add(startingPoint);
        while (!toDo.isEmpty()) {
            V<T> v = toDo.remove(0);
            Map<V<T>, Long> edges = g.edges(v);
            if (edges != null) {
                for (V<T> to : edges.keySet()) {
                    if (connected.add(to)) {
                        toDo.add(to);
                    }
                }
            }
        }
        return connected;
    }
}
