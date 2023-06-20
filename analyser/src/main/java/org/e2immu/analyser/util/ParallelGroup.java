package org.e2immu.analyser.util;

import java.util.Comparator;
import java.util.List;

public class ParallelGroup<T> implements ParSeq<T> {

    @Override
    public boolean containsParallels() {
        return true;
    }

    @Override
    public <X> List<X> sortParallels(List<X> items, Comparator<X> comparator) {
        return items.stream().sorted(comparator).toList();
    }

}
