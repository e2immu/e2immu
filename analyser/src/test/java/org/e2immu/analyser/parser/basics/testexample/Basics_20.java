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

import org.e2immu.annotation.*;

import java.util.ArrayList;
import java.util.List;

/*
simple forms of linking
 */
public class Basics_20 {

    @Container
    @Independent
    static class I {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    // T is transparent, but List<T> is not
    @FinalFields @Container
    static class C1<T> {
        private final List<T> list;

        public C1(List<T> list) { // implicitly @Dependent
            this.list = list;
        }

        // implicitly @Dependent
        public List<T> getListC1() {
            return list;
        }

        // implicitly @Independent1
        public T getFirstC1() {
            return list.get(0);
        }
    }

    public static void test1() {
        I i = new I();
        i.setI(9);
        List<I> list = new ArrayList<>();
        list.add(i);
        C1<I> ci = new C1<>(list); // ci linked0 to list
        C1<I> ci2 = new C1<>(new ArrayList<>(list));
        // ci2 linked0 to nAL, nAL linked1 to list -> ci2 linked1 to list
        System.out.println(ci + ", " + ci2);
    }

    // T is transparent, but List<T> is not
    @ImmutableContainer(hc = true)
    static class C2<T> {
        private final List<T> list;

        public C2(List<T> list) { // implicitly @Dependent
            this.list = new ArrayList<>(list);
        }

        @Independent
        public List<T> getListC2() {
            return new ArrayList<>(list);
        }

        // implicitly @Independent1
        public T getFirstC2() {
            return list.get(0);
        }
    }

    public static void test2() {
        I i = new I();
        i.setI(9);
        List<I> list = new ArrayList<>();
        list.add(i);
        C2<I> ci = new C2<>(list); // ci linked1 to list
        C2<I> ci2 = new C2<>(new C1<>(list).getListC1()); // linked1, linked0 -> linked1
        System.out.println(ci + ", " + ci2);
    }
}
