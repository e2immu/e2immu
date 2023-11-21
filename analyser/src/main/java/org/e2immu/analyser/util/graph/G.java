package org.e2immu.analyser.util.graph;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class G<T> {

    private final Set<V<T>> vertices;
    private final Map<V<T>, Map<V<T>, Long>> edges;
    private final Map<T, V<T>> elements;

    private G(Set<V<T>> vertices,
              Map<T, V<T>> elements,
              Map<V<T>, Map<V<T>, Long>> edges) {
        this.vertices = vertices;
        this.edges = edges;
        this.elements = elements;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof G<?> g && vertices.equals(g.vertices) && edges.equals(g.edges);
    }

    public static <T> G<T> create(Map<T, Map<T, Long>> initialGraph) {
        Set<V<T>> vertices = new HashSet<>();
        Map<V<T>, Map<V<T>, Long>> edges = new HashMap<>();
        Map<T, V<T>> elements = new HashMap<>();
        for (T t : initialGraph.keySet()) {
            V<T> v = new V<>(Set.of(t), 0, Set.of());
            vertices.add(v);
            elements.put(t, v);
        }
        for (Map.Entry<T, Map<T, Long>> entry : initialGraph.entrySet()) {
            V<T> from = elements.get(entry.getKey());
            for (Map.Entry<T, Long> e2 : entry.getValue().entrySet()) {
                V<T> to = elements.get(e2.getKey());
                edges.computeIfAbsent(from, f -> new HashMap<>()).put(to, e2.getValue());
            }
        }
        return new G<>(Set.copyOf(vertices), Map.copyOf(elements), Map.copyOf(edges));
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
        Map<V<T>, Map<V<T>, Long>> newEdges = new HashMap<>();
        for (Map.Entry<V<T>, Map<V<T>, Long>> entry : edges.entrySet()) {
            Set<V<T>> toRemove = edgesToRemove.apply(entry.getKey());
            Map<V<T>, Long> newEdgesOfV;
            if (toRemove != null && !toRemove.isEmpty()) {
                Map<V<T>, Long> map = new HashMap<>(entry.getValue());
                map.keySet().removeAll(toRemove);
                newEdgesOfV = Map.copyOf(map);
            } else {
                newEdgesOfV = entry.getValue();
            }
            if (newEdgesOfV.isEmpty()) {
                newEdges.remove(entry.getKey());
            } else {
                newEdges.put(entry.getKey(), newEdgesOfV);
            }
        }
        return new G<T>(vertices, elements, Map.copyOf(newEdges));
    }


    public G<T> withGroupedVertices(Set<V<T>> grouping) {
        throw new UnsupportedOperationException("NYI");
    }


    public G<T> subGraph(Set<V<T>> subSet) {
        Map<T, V<T>> newElements = new HashMap<>();
        Map<V<T>, Map<V<T>, Long>> newEdges = new HashMap<>();
        for (V<T> v : subSet) {
            for (T t : v.ts()) {
                newElements.put(t, v);
            }
            Map<V<T>, Long> localEdges = edges.get(v);
            if (localEdges != null) {
                Map<V<T>, Long> newLocal = new HashMap<>();
                for (Map.Entry<V<T>, Long> entry : localEdges.entrySet()) {
                    V<T> to = entry.getKey();
                    if (subSet.contains(to)) {
                        newLocal.put(to, entry.getValue());
                    }
                }
                if (!newLocal.isEmpty()) {
                    newEdges.put(v, Map.copyOf(newLocal));
                }
            }
        }
        return new G<T>(Set.copyOf(subSet), Map.copyOf(newElements), Map.copyOf(newEdges));
    }

    public G<T> mutableReverseSubGraph(Set<V<T>> subSet) {
        Map<T, V<T>> newElements = new HashMap<>();
        Map<V<T>, Map<V<T>, Long>> newEdges = new HashMap<>();
        for (V<T> v : subSet) {
            for (T t : v.ts()) {
                newElements.put(t, v);
            }
            Map<V<T>, Long> localEdges = edges.get(v);
            if (localEdges != null) {
                for (Map.Entry<V<T>, Long> entry : localEdges.entrySet()) {
                    V<T> to = entry.getKey();
                    Map<V<T>, Long> newLocal = newEdges.computeIfAbsent(to, t -> new HashMap<>());
                    newLocal.put(v, entry.getValue());
                }
            }
        }
        // freeze edge maps
        return new G<T>(subSet, newElements, newEdges);
    }

    public Set<V<T>> vertices() {
        return vertices;
    }

    public Map<V<T>, Long> edges(V<T> v) {
        return edges.get(v);
    }

    public Stream<E<T>> edgeStream() {
        return edges.entrySet().stream()
                .flatMap(e -> e.getValue().entrySet().stream()
                        .map(e2 -> new E<>(e.getKey(), e2.getKey(), e2.getValue())));
    }

    public V<T> vertex(T t) {
        return elements.get(t);
    }

    public Iterator<Map<V<T>, Map<V<T>, Long>>> edgeIterator(Comparator<Long> comparator, Long limit) {
        List<E<T>> edges = edgeStream()
                .filter(e -> limit == null || e.weight() < limit)
                .sorted((e1, e2) -> comparator.compare(e1.weight(), e2.weight()))
                .toList();
        return edges.stream().map(e -> Map.of(e.from(), Map.of(e.to(), e.weight()))).iterator();
    }
}
