package org.e2immu.graph.op;

import org.e2immu.graph.V;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
Leaves are in list.get(0)
Vertices with an edge to leaves only are in list.get(1)
Vertices with edges to those in list.get(0) and list.get(1) are in list.get(2), etc.
 */
public record Hierarchy<T>(List<Set<V<T>>> list) {
    @Override
    public String toString() {
        return list.stream()
                .map(s -> "[" + s.stream().map(V::toString).sorted().collect(Collectors.joining(", ")) + "]")
                .collect(Collectors.joining("; "));
    }

    public Stream<T> sortedStream(Comparator<T> comparator) {
        return list.stream().flatMap(set -> set.stream().map(V::t).sorted(comparator));
    }

    public void append(Hierarchy<T> other) {
        list.addAll(other.list);
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public int size() {
        return list.size();
    }
}
