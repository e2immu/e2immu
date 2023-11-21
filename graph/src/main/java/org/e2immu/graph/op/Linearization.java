package org.e2immu.graph.op;

import org.e2immu.graph.G;
import org.e2immu.graph.V;

import java.util.*;
import java.util.stream.Collectors;

public class Linearization {

    public record Result<T>(List<Set<V<T>>> linearized,
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

    public static <T> int qualityBasedOnTotalCluster(G<T> g) {
        return linearize(g, LinearizationMode.ONLY_REVERSE_GRAPH).quality();
    }

    public static <T> Result<T> linearize(G<T> g) {
        return linearize(g, LinearizationMode.ALL);
    }

    public enum LinearizationMode {
        ONLY_LINEAR, ONLY_REVERSE_GRAPH, ALL
    }

    public static <T> Result<T> linearize(G<T> g, LinearizationMode mode) {
        Set<V<T>> toDo = new HashSet<>(g.vertices());
        Set<V<T>> done = new HashSet<>();
        List<Set<V<T>>> linearResult = new ArrayList<>();
        List<Set<V<T>>> cycles = new ArrayList<>();
        List<V<T>> attachedToCycle = new ArrayList<>();
        int n = toDo.size();
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
                if (mode == LinearizationMode.ONLY_REVERSE_GRAPH || mode == LinearizationMode.ALL) {
                    // the remaining vertices form one or more cycles; but they can still be pruned a bit
                    attachedToCycle.addAll(removeAsManyAsPossible(g, toDo));
                    attachedToCycle.forEach(t -> {
                        toDo.remove(t);
                        done.add(t);
                    });
                    // find the connected cycles in the remaining vertices
                    if (mode == LinearizationMode.ONLY_REVERSE_GRAPH) {
                        cycles.add(toDo);
                    } else {
                        G<T> subGraph = g.subGraph(toDo);
                        cycles.addAll(findConnectedSubSets(subGraph, toDo));
                    }
                }
                break;
            } else {
                done.addAll(localLinear);
                localLinear.forEach(toDo::remove);
                linearResult.add(localLinear);
            }
        }
        return new Result<>(linearResult, attachedToCycle, cycles);
    }

    private static <T> List<V<T>> removeAsManyAsPossible(G<T> g, Set<V<T>> toDo) {
        G<T> reverseSub = g.mutableReverseSubGraph(toDo);
        Result<T> r = linearize(reverseSub, LinearizationMode.ONLY_LINEAR);
        return r.linearized.stream().flatMap(Collection::stream).toList();
    }

    public static <T> List<Set<V<T>>> findConnectedSubSets(G<T> g, Set<V<T>> startingPoints) {
        List<Set<V<T>>> result = new ArrayList<>();
        Set<V<T>> toDo = new HashSet<>(startingPoints);
        while (!toDo.isEmpty()) {
            V<T> v = toDo.stream().findFirst().orElseThrow();
            Set<V<T>> connected = Common.follow(g, v);
            toDo.removeAll(connected);
            result.add(connected);
            tryToMergeResultSets(result);
        }
        return result;
    }

    private static <T> void tryToMergeResultSets(List<Set<V<T>>> result) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Integer mergeI = null;
            Integer mergeJ = null;
            for (int i = 0; i < result.size() - 1; i++) {
                Set<V<T>> set1 = result.get(i);
                for (int j = i + 1; j < result.size(); j++) {
                    Set<V<T>> set2 = result.get(j);
                    if (!Collections.disjoint(set1, set2)) {
                        mergeI = i;
                        mergeJ = j;
                        break;
                    }
                }
                if (mergeI != null) break;
            }
            if (mergeI != null) {
                changed = true;
                result.get(mergeI).addAll(result.get(mergeJ));
                result.remove((int) mergeJ);
            }
        }
    }

}
