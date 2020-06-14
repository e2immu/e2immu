package org.e2immu.analyser.util.stream;

import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class SlidingWindowOf2 {

    static class PreviousAndCurrent<T> {
        public final T current;
        public final T previous;

        public PreviousAndCurrent(T previous, T current) {
            this.current = current;
            this.previous = previous;
        }
    }

    static <T> Stream<PreviousAndCurrent<T>> streamWith2(Iterable<T> input) {
        Iterable<PreviousAndCurrent<T>> iterable = () -> new SlidingWindowIterator<T>(input.iterator());
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private static class SlidingWindowIterator<T> implements Iterator<PreviousAndCurrent<T>> {
        private final Iterator<T> iterator;
        private T previous;

        SlidingWindowIterator(Iterator<T> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public PreviousAndCurrent<T> next() {
            PreviousAndCurrent<T> result = new PreviousAndCurrent<>(previous, iterator.next());
            previous = result.current;
            return result;
        }
    }
}
