package org.e2immu.graph;

// the vertex type of the graph
public class V<T> {
    private final T t;
    private final int hashCode;

    public V(T t) {
        this.t = t;
        hashCode = t.hashCode();
    }

    @Override
    public String toString() {
        return t.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        return obj instanceof V<?> v && t.equals(v.t);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public T t() {
        return t;
    }
}
