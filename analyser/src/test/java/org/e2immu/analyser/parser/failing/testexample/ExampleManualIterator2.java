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

package org.e2immu.analyser.parser.failing.testexample;

import org.e2immu.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
Move from a @E2Container to an @Independent @Container, keeping track of some modifications;
however, list remains @SupportData @NotModified
 */
@Container
@Independent
public class ExampleManualIterator2<E> {

    @NotModified
    private final List<E> list = new ArrayList<>();

    @Final(absent = true)
    private int someCounter;

    public int incrementAndGet() {
        return ++someCounter;
    }

    @Independent
    public ExampleManualIterator2(E[] es) {
        Collections.addAll(list, es);
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
        public boolean hasNext() {
            return index < list.size();
        }

        @Override
        public E next() {
            return list.get(index++);
        }
    }

    interface MyConsumer<E> {
        void accept(E e);
    }

    @Modified // now there are other methods that allow modifications
    public void visit(@NotModified MyConsumer<E> consumer) { // follows because there are no means of self-modifying
        for (E e : list) consumer.accept(e);
    }
}
