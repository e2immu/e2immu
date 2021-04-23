package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.Iterator;
import java.util.List;

@E2Container
public class TypeIndependence_1<T> implements Iterable<T> {

    @E2Container(absent = true)
    @NotNull1
    private final T[] elements;

    @SuppressWarnings("unchecked")
    public TypeIndependence_1(List<T> input) {
        this.elements = (T[]) input.toArray();
    }

    @Override
    @Independent
    public Iterator<T> iterator() {
        return new IteratorImpl<T>(elements);
    }

    @Container
    @Independent
    static class IteratorImpl<T> implements Iterator<T> {
        private int i;
        private final T[] elements;

        private IteratorImpl(T[] elements) {
            this.elements = elements;
        }

        @Override
        public boolean hasNext() {
            return i < elements.length;
        }

        @Override
        @NotNull
        @E2Container(absent = true)
        public T next() {
            return elements[i++];
        }
    }
}
