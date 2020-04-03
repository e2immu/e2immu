/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.testexample;

import java.util.List;
import java.util.stream.Collectors;

public class TypeParameters {

    static class Example1 {
        private List<C> strings;

        static class C<T> {
            final String s;

            C(String s) {
                this.s = s;
            }

            C(T t) {
                s = t.toString();
            }
        }

        public Example1(List<Integer> input) {
            strings = input.stream().map(C::new).collect(Collectors.toList());

        }

        public List<C> getStrings() {
            return strings;
        }
    }

    static class Example2<T> {
        private List<C2> strings2;

        static class C2<T> {
            final String s2;

            C2(String s2p) {
                this.s2 = s2p;
            }

            C2(T t2p) {
                s2 = t2p.toString();
            }
        }

        public Example2(List<T> input2) {
            strings2 = input2.stream().map(C2::new).collect(Collectors.toList());
        }

        public List<C2> getStrings2() {
            return strings2;
        }
    }

    static class Example3<E extends Comparable<E>> {
        private final int bound;

        public Example3(int bound) {
            this.bound = bound;
        }

        boolean better(E e31, E e32) {
            return e31.compareTo(e32) > bound;
        }
    }

    static class Example4<E extends Comparable<? super E>> {
        private final int bound;

        public Example4(int bound) {
            this.bound = bound;
        }

        boolean worse(E e41, E e42) {
            return e41.compareTo(e42) < bound;
        }
    }
}
