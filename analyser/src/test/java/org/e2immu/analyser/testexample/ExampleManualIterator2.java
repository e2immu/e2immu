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

@Container
@Independent
public class ExampleManualIterator2<E> {

    @SupportData
    private final List<E> list = new ArrayList<>();

    @Variable
    private int someCounter;

    public int incrementAndGet() {
        return ++someCounter;
    }

    @Independent
    public ExampleManualIterator2(E[] es) {
        Collections.addAll(list, es);
    }

    interface MyIterator<E> {
        boolean hasNext();

        E next();
    }

    @Independent
    public MyIterator<E> iterator() {
        return new MyIteratorImpl<>(list);
    }

    @Independent
    @Container
    public static class MyIteratorImpl<E> implements MyIterator<E> {

        @SupportData
        @NotModified
        private final List<E> list;
        @Modified
        private int index;

        @Independent // 4, otherwise 3 cannot be independent
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

    @FunctionalInterface
    interface MyConsumer<E> {
        void accept(E e);
    }

    @NotModified // otherwise we cannot have @E2Immutable; can be computed: no means of self-modifying, + @Exposed solves mods on E
    public void visit(@NotModified @Exposed MyConsumer<E> consumer) { // follows because there are no means of self-modifying
        for (E e : list) consumer.accept(e);
    }
}
