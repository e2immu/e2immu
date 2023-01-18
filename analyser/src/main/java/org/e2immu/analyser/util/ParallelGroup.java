package org.e2immu.analyser.util;

import org.e2immu.analyser.model.MethodInfo;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class ParallelGroup<T> implements ParSeq<T> {

    public static final String NOT_IMPLEMENTED = "Not implemented";

    @Override
    public ParSeq<T> before(ParSeq<T> other) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public ParSeq<T> inParallelWith(ParSeq<T> other, MethodInfo operator) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public boolean contains(T t) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public boolean contains(ParSeq<T> other) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public ParSeq<T> intersection(ParSeq<T> other) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public boolean containsParallels() {
        return true;
    }

    @Override
    public List<T> toList() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public <X> List<X> sortParallels(List<X> items, Comparator<X> comparator) {
        return items.stream().sorted(comparator).toList();
    }

    @Override
    public <X> ParSeq<X> map(Function<T, X> function) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public ParSeq<T> apply(List<ParSeq<T>> list, MethodInfo operator) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }
}
