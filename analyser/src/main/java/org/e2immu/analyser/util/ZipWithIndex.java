package org.e2immu.analyser.util;

import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ZipWithIndex {

    static class WithIndex<T> {
        public final T t;
        public final int index;

        public WithIndex(T t, int index) {
            this.index = index;
            this.t = t;
        }
    }

    /*
    goal: if with a list
     */

    static <T> Stream<WithIndex<T>> streamWithIndex(Iterable<T> input) {
        Iterable<WithIndex<T>> iterable = () -> new ZippedIterator<T>(input.iterator());
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private static class ZippedIterator<T> implements Iterator<WithIndex<T>> {
        private final Iterator<T> iterator;
        private int index;

        ZippedIterator(Iterator<T> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public WithIndex<T> next() {
            return new WithIndex<>(iterator.next(), index++);
        }
    }
}
