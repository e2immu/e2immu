package org.e2immu.analyser.util.graph;

import java.util.Set;

// the vertex type of the graph
public class V<T> {
    private final Set<T> ts;
    private final long internalEdgeWeight;
    private final Set<V<T>> grouping;
    private final int hashCode;

    public V(Set<T> ts, long internalEdgeWeight, Set<V<T>> grouping) {
        this.ts = ts;
        this.internalEdgeWeight = internalEdgeWeight;
        this.grouping = grouping;
        hashCode = ts.hashCode();
    }

    // for testing
    public V(T t) {
        this(Set.of(t), 0, Set.of());
    }

    @Override
    public String toString() {
        return ts.toString()
                + (internalEdgeWeight == 0 ? "" : (":" + internalEdgeWeight))
                + (grouping.isEmpty() ? "" : (":" + grouping));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        return obj instanceof V<?> v && ts.equals(v.ts);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public Set<T> ts() {
        return ts;
    }

    public long internalEdgeWeight() {
        return internalEdgeWeight;
    }

    public Set<V<T>> grouping() {
        return grouping;
    }
}
