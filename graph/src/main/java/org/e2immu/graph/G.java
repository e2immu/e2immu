package org.e2immu.graph;

import java.util.*;
import java.util.function.Function;
import java.util.function.LongBinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
This class uses linked maps and sets (as opposed to Map.copyOf, Set.copyOf, new HashMap(), etc.)
to behave in a consistent way across tests.
 */
public class G<T> {
    private final Set<V<T>> vertices;
    private final Map<V<T>, Map<V<T>, Long>> edges;

    private G(Set<V<T>> vertices,
              Map<V<T>, Map<V<T>, Long>> edges) {
        this.vertices = vertices;
        this.edges = edges;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof G<?> g && vertices.equals(g.vertices) && edges.equals(g.edges);
    }

    public static <T> G<T> create(Map<T, Map<T, Long>> initialGraph) {
        Set<V<T>> vertices = new LinkedHashSet<>();
        Map<V<T>, Map<V<T>, Long>> edges = new LinkedHashMap<>();
        Map<T, V<T>> elements = new LinkedHashMap<>();
        for (T t : initialGraph.keySet()) {
            V<T> v = new V<>(t);
            vertices.add(v);
            elements.put(t, v);
        }
        for (Map.Entry<T, Map<T, Long>> entry : initialGraph.entrySet()) {
            V<T> from = elements.get(entry.getKey());
            for (Map.Entry<T, Long> e2 : entry.getValue().entrySet()) {
                V<T> to = elements.get(e2.getKey());
                assert to != null;
                edges.computeIfAbsent(from, f -> new LinkedHashMap<>()).put(to, e2.getValue());
            }
        }
        return new G<>(vertices, edges);
    }

    // ! expensive operation, no map
    public V<T> vertex(T t) {
        return vertices.stream().filter(v -> t.equals(v.t())).findFirst().orElseThrow();
    }

    // based on a map of T elements
    public static class Builder<T> {
        private final LongBinaryOperator sum;

        public Builder(LongBinaryOperator sum) {
            this.sum = sum;
        }

        Map<T, Map<T, Long>> map = new LinkedHashMap<>();

        public void addVertex(T t) {
            ensureVertex(t);
        }

        private Map<T, Long> ensureVertex(T t) {
            assert t != null;
            return map.computeIfAbsent(t, f -> new LinkedHashMap<>());
        }

        public void mergeEdge(T from, T to, long weight) {
            ensureVertex(to);
            ensureVertex(from).merge(to, weight, sum::applyAsLong);
        }

        public G<T> build() {
            return create(map);
        }

        public Iterable<Map.Entry<T, Map<T, Long>>> edges() {
            return () -> map.entrySet().iterator();
        }

        public void add(T from, Collection<T> tos) {
            Map<T, Long> m = ensureVertex(from);
            tos.forEach(to -> {
                ensureVertex(to);
                m.merge(Objects.requireNonNull(to), 1L, Long::sum);
            });
        }
    }

    @Override
    public String toString() {
        return edges.entrySet().stream()
                .flatMap(e -> e.getValue().entrySet().stream().map(e2 -> new E<>(e.getKey(), e2.getKey(), e2.getValue())))
                .map(Record::toString).sorted().collect(Collectors.joining(", "));
    }

    public G<T> withFewerEdges(Map<V<T>, Set<V<T>>> edgesToRemove) {
        return internalWithFewerEdges(edgesToRemove::get);
    }

    public G<T> withFewerEdgesMap(Map<V<T>, Map<V<T>, Long>> edgesToRemove) {
        return internalWithFewerEdges(v -> edgesToRemove.getOrDefault(v, Map.of()).keySet());
    }

    private G<T> internalWithFewerEdges(Function<V<T>, Set<V<T>>> edgesToRemove) {
        Map<V<T>, Map<V<T>, Long>> newEdges = new LinkedHashMap<>();
        for (Map.Entry<V<T>, Map<V<T>, Long>> entry : edges.entrySet()) {
            Set<V<T>> toRemove = edgesToRemove.apply(entry.getKey());
            Map<V<T>, Long> newEdgesOfV;
            if (toRemove != null && !toRemove.isEmpty()) {
                Map<V<T>, Long> map = new LinkedHashMap<>(entry.getValue());
                map.keySet().removeAll(toRemove);
                newEdgesOfV = map;
            } else {
                newEdgesOfV = entry.getValue();
            }
            if (newEdgesOfV.isEmpty()) {
                newEdges.remove(entry.getKey());
            } else {
                newEdges.put(entry.getKey(), newEdgesOfV);
            }
        }
        return new G<T>(vertices, newEdges);
    }

    public G<T> subGraph(Set<V<T>> subSet) {
        Map<V<T>, Map<V<T>, Long>> newEdges = new LinkedHashMap<>();
        for (V<T> v : subSet) {
            Map<V<T>, Long> localEdges = edges.get(v);
            if (localEdges != null) {
                Map<V<T>, Long> newLocal = new LinkedHashMap<>();
                for (Map.Entry<V<T>, Long> entry : localEdges.entrySet()) {
                    V<T> to = entry.getKey();
                    if (subSet.contains(to)) {
                        newLocal.put(to, entry.getValue());
                    }
                }
                if (!newLocal.isEmpty()) {
                    newEdges.put(v, newLocal);
                }
            }
        }
        return new G<T>(new LinkedHashSet<>(subSet), newEdges);
    }

    public G<T> mutableReverseSubGraph(Set<V<T>> subSet) {
        Map<V<T>, Map<V<T>, Long>> newEdges = new LinkedHashMap<>();
        for (V<T> v : subSet) {
            Map<V<T>, Long> localEdges = edges.get(v);
            if (localEdges != null) {
                for (Map.Entry<V<T>, Long> entry : localEdges.entrySet()) {
                    V<T> to = entry.getKey();
                    Map<V<T>, Long> newLocal = newEdges.computeIfAbsent(to, t -> new LinkedHashMap<>());
                    newLocal.put(v, entry.getValue());
                }
            }
        }
        // freeze edge maps
        return new G<T>(subSet, newEdges);
    }

    public Set<V<T>> vertices() {
        return vertices;
    }

    public Map<V<T>, Long> edges(V<T> v) {
        assert v != null;
        return edges.get(v);
    }

    public Iterable<Map.Entry<V<T>, Map<V<T>, Long>>> edges() {
        return () -> edges.entrySet().iterator();
    }

    public Stream<E<T>> edgeStream() {
        return edges.entrySet().stream()
                .flatMap(e -> e.getValue().entrySet().stream()
                        .map(e2 -> new E<>(e.getKey(), e2.getKey(), e2.getValue())));
    }

    public Iterator<Map<V<T>, Map<V<T>, Long>>> edgeIterator(Comparator<Long> comparator, Long limit) {
        List<E<T>> edges = edgeStream()
                .filter(e -> limit == null || e.weight() < limit)
                .sorted((e1, e2) -> comparator.compare(e1.weight(), e2.weight()))
                .toList();
        return edges.stream().map(e -> Map.of(e.from(), Map.of(e.to(), e.weight()))).iterator();
    }

    public Map<V<T>, Long> incomingVertexWeight(LongBinaryOperator sum) {
        Map<V<T>, Long> map = new HashMap<>();
        for (Map<V<T>, Long> targets : edges.values()) {
            for (Map.Entry<V<T>, Long> entry : targets.entrySet()) {
                map.merge(entry.getKey(), entry.getValue(), sum::applyAsLong);
            }
        }
        return map;
    }
}
