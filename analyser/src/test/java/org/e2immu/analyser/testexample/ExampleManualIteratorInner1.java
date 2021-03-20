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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@E2Container
public class ExampleManualIteratorInner1<E> {

    @NotModified
    private final List<E> list = new ArrayList<>();

    @Independent
    public ExampleManualIteratorInner1(E[] es) {
        Collections.addAll(list, es);
    }

    interface MyIterator<E> {
        boolean hasNext();

        E next();
    }

    @Independent
    // implies @NotModified; Otherwise we cannot have @E2Container; is @Independent because MyIteratorImpl is @Independent
    public MyIterator<E> iterator() {
        return new MyIteratorImpl();
    }

    @Container
    @Independent // because impossible to modify
    public class MyIteratorImpl implements MyIterator<E> {

        @Modified
        private int index;

        @Override
        @NotModified
        public boolean hasNext() {
            return index < list.size();
        }

        @Override
        @Modified
        @NotNull
        public E next() {
            return list.get(index++);
        }
    }

    @FunctionalInterface
    interface MyConsumer<E> {
        @Modified
        void accept(@Modified @NotNull E e);
    }

    // @Independent not needed, void method
    @NotModified // otherwise we cannot have @E2Immutable; can be computed:
    // "e"'s type is not support-data, so accept does not make a modification
    // there are no other modifying methods to allow for self-modification
    public void visit(@NotNull1 MyConsumer<E> consumer) { // follows because there are no means of self-modifying
        for (E e : list) consumer.accept(e);
    }
}
