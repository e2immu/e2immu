/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
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
