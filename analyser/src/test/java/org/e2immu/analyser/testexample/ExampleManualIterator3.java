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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.List;

/*
the independence of the iterator remains working on a `list` which is @Modified rather than @NotModified @SupportData
 */
@Container
public class ExampleManualIterator3<E> {

    @Modified
    private final List<E> list;

    public ExampleManualIterator3(List<E> list) {
        this.list = list;
    }

    @Modified
    public void add(E e) {
        list.add(e);
    }

    interface MyIterator<E> {
        @Modified
        boolean hasNext();

        @Modified
        E next();
    }

    @Independent
    public MyIterator<E> iterator() {
        return new MyIteratorImpl<>(list);
    }

    @Independent
    @Container
    public static class MyIteratorImpl<E> implements MyIterator<E> {

        @NotModified
        private final List<E> list;
        @Modified
        private int index;

        private MyIteratorImpl(@NotModified List<E> list) { // 2.
            this.list = list;
        }

        @Override
        @NotModified
        public boolean hasNext() {
            return index < list.size();
        }

        @Override
        @Modified
        public E next() {
            return list.get(index++);
        }
    }

    interface MyConsumer<E> {
        void accept(E e);
    }

    @Modified
    public void visit(@NotModified MyConsumer<E> consumer) { // follows because there are no means of self-modifying
        for (E e : list) consumer.accept(e);
    }
}
