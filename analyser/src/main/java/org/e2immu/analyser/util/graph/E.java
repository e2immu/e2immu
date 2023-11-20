package org.e2immu.analyser.util.graph;

public record E<T>(V<T> from, V<T> to, long weight) {
    @Override
    public String toString() {
        return from + "->" + weight + "->" + to;
    }
}
