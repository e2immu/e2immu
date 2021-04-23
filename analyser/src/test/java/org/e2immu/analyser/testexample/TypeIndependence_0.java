package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.Iterator;
import java.util.List;

@E2Container
public class TypeIndependence_0<T> implements Iterable<T> {

    @E2Container(absent = true)
    @NotNull1
    private final T[] elements;

    @SuppressWarnings("unchecked")
    public TypeIndependence_0(List<T> input) {
        this.elements = (T[]) input.toArray();
    }

    @Override
    @Independent
    public Iterator<T> iterator() {
        return new IteratorImpl();
    }

    @Container
    @Independent
    class IteratorImpl implements Iterator<T> {
        private int i;

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
