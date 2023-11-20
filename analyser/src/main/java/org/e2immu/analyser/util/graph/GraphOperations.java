package org.e2immu.analyser.util.graph;

import org.e2immu.analyser.util.DependencyGraph;

import java.util.*;

public class GraphOperations {

    public record Result<T>(List<V<T>> linearized, List<V<T>> notProblematic, List<G<T>> remainingCycles) {
    }

    public static <T> Result<T> linearize(G<T> g) {
        return linearize(g, false);
    }

    public static <T> Result<T> linearize(G<T> g, boolean onlyLinear) {
        Set<V<T>> toDo = new HashSet<>(g.vertices());
        Set<V<T>> done = new HashSet<>();
        List<V<T>> linearResult = new ArrayList<>();
        List<G<T>> cycles = new ArrayList<>();
        List<V<T>> notEssentialForCycle = new ArrayList<>();
        while (!toDo.isEmpty()) {
            List<V<T>> localLinear = new LinkedList<>();
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
                    done.add(v);
                }
            }
            if (localLinear.isEmpty()) {
                if (!onlyLinear) {
                    // the remaining vertices form one or more cycles; but they can still be pruned a bit
                    notEssentialForCycle.addAll(removeAsManyAsPossible(g, toDo));
                    notEssentialForCycle.forEach(t -> {
                        toDo.remove(t);
                        done.add(t);
                    });
                    // find the connected cycles in the remaining vertices

                }
                break;
            } else {
                localLinear.forEach(toDo::remove);
                linearResult.addAll(localLinear);
            }
        }
        return new Result<>(List.copyOf(linearResult), List.copyOf(notEssentialForCycle), List.copyOf(cycles));
    }

    private static <T> List<V<T>> removeAsManyAsPossible(G<T> g, Set<V<T>> toDo) {
        G<T> reverseSub = g.reverseSubGraph(toDo);
        Result<T> result = linearize(reverseSub, true);
        return result.linearized;
    }
}
