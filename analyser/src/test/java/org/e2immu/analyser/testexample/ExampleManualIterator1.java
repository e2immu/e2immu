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

@E2Container
public class ExampleManualIterator1<E> {

    @NotModified
    private final List<E> list = new ArrayList<>();

    @Independent
    public ExampleManualIterator1(E[] es) {
        Collections.addAll(list, es);
    }

    interface MyIterator<E> {
        boolean hasNext();

        E next();
    }

    @Independent
    // implies @NotModified; Otherwise we cannot have @E2Container; is @Independent because MyIteratorImpl is @Independent
    public MyIterator<E> iterator() {
        return new MyIteratorImpl<>(list);
    }

    @Container
    @Independent // because impossible to modify
    public static class MyIteratorImpl<E> implements MyIterator<E> {

        @NotModified
        private final List<E> list;
        @Variable
        private int index;

        @Dependent
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
