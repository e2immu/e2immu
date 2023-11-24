package org.e2immu.graph.op;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record Cycles<T>(Set<Cycle<T>> cycles) implements Iterable<Cycle<T>> {

    @Override
    public String toString() {
        return cycles.stream().map(cycle -> "[" + cycle + "]").sorted().collect(Collectors.joining("; "));
    }

    public Stream<T> sortedStream(Comparator<T> comparator) {
        CycleComparator cc = new CycleComparator(comparator);
        return cycles.stream().sorted(cc).flatMap(cycle -> cycle.sortedStream(comparator));
    }

    public boolean isEmpty() {
        return cycles.isEmpty();
    }

    public int size() {
        return cycles.size();
    }

    @Override
    public Iterator<Cycle<T>> iterator() {
        return cycles.iterator();
    }

    class CycleComparator implements Comparator<Cycle<T>> {
        private final Comparator<T> comparator;

        CycleComparator(Comparator<T> comparator) {
            this.comparator = comparator;
        }

        @Override
        public int compare(Cycle<T> o1, Cycle<T> o2) {
            int c = o2.size() - o1.size();
            if (c != 0) return c;
            T t1 = o1.first(comparator);
            T t2 = o2.first(comparator);
            assert !t1.equals(t2); // cycles should be disjoint
            return comparator.compare(t1, t2);
        }
    }
}
