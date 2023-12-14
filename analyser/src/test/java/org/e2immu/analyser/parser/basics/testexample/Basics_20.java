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

package org.e2immu.analyser.parser.basics.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.Independent;

import java.util.ArrayList;
import java.util.List;

/*
simple forms of linking
 */
public class Basics_20 {

    @Container
    @Independent
    static class I implements Comparable<I> {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }

        @Override
        public int compareTo(I o) {
            return i - o.i;
        }
    }

    @FinalFields
    @Container
    static class C1<T> {
        private final List<T> list;

        public C1(List<T> list) { // implicitly @Dependent
            this.list = list;
        }

        @Independent(absent = true)
        public List<T> getListC1() {
            return list;
        }

        @Independent(hc = true)
        public T getFirstC1() {
            return list.get(0);
        }
    }

    public static void test1() {
        I i = new I();
        i.setI(9);
        List<I> list = new ArrayList<>();
        list.add(i);
        C1<I> ci = new C1<>(list); // ci linked dependently to list
        C1<I> ci2 = new C1<>(new ArrayList<>(list));
        System.out.println(ci + ", " + ci2);
    }

    @ImmutableContainer(hc = true)
    static class C2<T> {
        private final List<T> list;

        public C2(List<T> list) { // implicitly @Dependent
            this.list = new ArrayList<>(list);
        }

        @Independent(hc = true)
        public List<T> getListC2() {
            return new ArrayList<>(list);
        }

        @Independent(hc = true) // implicit
        public T getFirstC2() {
            return list.get(0);
        }
    }

    public static void test2() {
        I i = new I();
        i.setI(9);
        List<I> list = new ArrayList<>();
        list.add(i);
        C2<I> ci = new C2<>(list);
        C2<I> ci2 = new C2<>(new C1<>(list).getListC1());
        System.out.println(ci + ", " + ci2);
    }

    /*
     Identical to C2, but now with Object rather than a type parameter.
     Links and dependencies should stay the same.
     */

    @ImmutableContainer(hc = true)
    static class C3 {
        private final List<Object> list;

        public C3(List<Object> list) {
            this.list = new ArrayList<>(list);
        }

        @Independent(hc = true)
        public List<Object> getListC3() {
            return new ArrayList<>(list);
        }

        @Independent(hc = true)
        public Object getFirstC3() {
            return list.get(0);
        }
    }

    public static void test3() {
        I i = new I();
        i.setI(9);
        List<Object> list = new ArrayList<>();
        list.add(i);
        C3 ci = new C3(list);
        C3 ci3 = new C3(new C1<>(list).getListC1());
        System.out.println(ci + ", " + ci3);
    }

    /*
     Identical to C2, but now with a bound type parameter.
     Links and dependencies should stay the same.
     */
    @ImmutableContainer(hc = true)
    static class C4<T extends Comparable<? super T>> {
        private final List<T> list;

        public C4(List<T> list) {
            this.list = new ArrayList<>(list);
        }

        @Independent(hc = true)
        public List<T> getListC4() {
            return new ArrayList<>(list);
        }

        @Independent(hc = true)
        public T getFirstC4() {
            return list.get(0);
        }
    }

    public static void test4() {
        I i = new I();
        i.setI(9);
        List<I> list = new ArrayList<>();
        list.add(i);
        C4<I> ci = new C4<>(list);
        C4<I> ci4 = new C4<>(new C1<>(list).getListC1());
        System.out.println(ci + ", " + ci4);
    }

}
