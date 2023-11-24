package org.e2immu.graph.op;

import org.e2immu.graph.V;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record Cycle<T>(Set<V<T>> vertices) {
    public int size() {
        return vertices().size();
    }

    @Override
    public String toString() {
        return vertices.stream().map(V::toString).sorted().collect(Collectors.joining(", "));
    }

    public Stream<T> sortedStream(Comparator<T> comparator) {
        return vertices.stream().map(V::t).sorted(comparator);
    }

    public T first(Comparator<T> comparator) {
        return vertices().stream().map(V::t).min(comparator).orElseThrow();
    }

    public boolean contains(V<T> v) {
        return vertices.contains(v);
    }
}
