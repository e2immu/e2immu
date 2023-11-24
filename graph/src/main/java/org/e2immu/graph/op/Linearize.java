package org.e2immu.graph.op;

import org.e2immu.graph.G;
import org.e2immu.graph.V;

import java.util.*;
import java.util.stream.Stream;

public class Linearize {

    public record Result<T>(Hierarchy<T> linearized,
                            Hierarchy<T> attachedToCycles,
                            Cycles<T> remainingCycles) {

        /*
        The quality of an operation is measured by the size of the largest cycle after linearization.
         */
        public int quality() {
            return remainingCycles.cycles().stream().mapToInt(Cycle::size).max().orElse(0);
        }

        @Override
        public String toString() {
            return "L=" + linearized + " P=" + attachedToCycles + " R=" + remainingCycles;
        }

        public List<T> asList(Comparator<T> comparator) {
            Stream<T> s1 = linearized.sortedStream(comparator);
            Stream<T> s2 = remainingCycles.sortedStream(comparator);
            Stream<T> s3 = attachedToCycles.sortedStream(comparator);
            return Stream.concat(s1, Stream.concat(s2, s3)).toList();
        }

        private static <T> Stream<T> sortedStream(List<Set<V<T>>> linearized, Comparator<T> comparator) {
            return linearized.stream().flatMap(set -> set.stream().map(V::t).sorted(comparator));
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
        Set<V<T>> toDo = new LinkedHashSet<>(g.vertices());
        Set<V<T>> done = new LinkedHashSet<>();
        List<Set<V<T>>> linearResult = new ArrayList<>();
        Set<Cycle<T>> cycleSet = new LinkedHashSet<>();
        Hierarchy<T> attachedToCycles = new Hierarchy<>(new ArrayList<>());
        while (!toDo.isEmpty()) {
            Set<V<T>> localLinear = new LinkedHashSet<>();
            for (V<T> v : toDo) {
                Map<V<T>, Long> edgeMap = g.edges(v);
                Set<V<T>> edges = edgeMap == null ? null : edgeMap.keySet();
                boolean safe;
                if (edges == null || edges.isEmpty()) {
                    safe = true;
                } else {
                    Set<V<T>> copy = new LinkedHashSet<>(edges);
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
                    attachedToCycles.append(removeAsManyAsPossible(g, toDo));
                    attachedToCycles.list().stream().flatMap(Set::stream).forEach(t -> {
                        toDo.remove(t);
                        done.add(t);
                    });
                    // find the connected cycles in the remaining vertices
                    if (mode == LinearizationMode.ONLY_REVERSE_GRAPH) {
                        cycleSet.add(new Cycle<>(toDo));
                    } else {
                        G<T> subGraph = g.subGraph(toDo);
                        cycleSet.addAll(findConnectedSubSets(subGraph, toDo));
                    }
                }
                break;
            } else {
                done.addAll(localLinear);
                localLinear.forEach(toDo::remove);
                linearResult.add(localLinear);
            }
        }
        Cycles<T> cycles = new Cycles<>(cycleSet);
        return new Result<>(new Hierarchy<>(linearResult), attachedToCycles, cycles);
    }

    private static <T> Hierarchy<T> removeAsManyAsPossible(G<T> g, Set<V<T>> toDo) {
        G<T> reverseSub = g.mutableReverseSubGraph(toDo);
        Result<T> r = linearize(reverseSub, LinearizationMode.ONLY_LINEAR);
        return r.linearized.reversed();
    }

    public static <T> List<Cycle<T>> findConnectedSubSets(G<T> g, Set<V<T>> startingPoints) {
        List<Cycle<T>> result = new ArrayList<>();
        Set<V<T>> toDo = new LinkedHashSet<>(startingPoints);
        while (!toDo.isEmpty()) {
            V<T> v = toDo.stream().findFirst().orElseThrow();
            Set<V<T>> connected = Common.follow(g, v);
            toDo.removeAll(connected);
            result.add(new Cycle<>(connected));
            tryToMergeResultSets(result);
        }
        return result;
    }

    private static <T> void tryToMergeResultSets(List<Cycle<T>> result) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Integer mergeI = null;
            Integer mergeJ = null;
            for (int i = 0; i < result.size() - 1; i++) {
                Cycle<T> c1 = result.get(i);
                for (int j = i + 1; j < result.size(); j++) {
                    Cycle<T> c2 = result.get(j);
                    if (!Collections.disjoint(c1.vertices(), c2.vertices())) {
                        mergeI = i;
                        mergeJ = j;
                        break;
                    }
                }
                if (mergeI != null) break;
            }
            if (mergeI != null) {
                changed = true;
                result.get(mergeI).vertices().addAll(result.get(mergeJ).vertices());
                result.remove((int) mergeJ);
            }
        }
    }

}
