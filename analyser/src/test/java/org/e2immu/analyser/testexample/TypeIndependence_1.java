/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
