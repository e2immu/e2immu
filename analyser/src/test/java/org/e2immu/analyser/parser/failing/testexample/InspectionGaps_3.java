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


import java.util.Comparator;

public class InspectionGaps_3 {

    interface MyComparable<T> {
        int compareTo(T t);
    }

    interface Node extends MyComparable<Node> {
    }

    interface Sub1 extends Node {
    }

    interface Sub2 extends Node {
    }

    interface MyList1<T extends Node> extends MyComparable<MyList1<T>> {
        void add(T t);
    }

    interface MyList2<T extends Node> extends MyComparable<MyList2<? super T>> {
    }

    interface MyList3<T extends Node> extends MyComparable<MyList3<? extends T>> {
    }

    static <T> void method(T t) {
        Object object = t;
        // FAILS: t = new Object();
    }

    static int compare(Integer integer) {
        return integer.compareTo(3); // box int -> Integer
    }

    static int compare(int i, int j) {
        return i - j;
    }

    static Comparator<Integer> makeComparator() {
        return InspectionGaps_3::compare;
    }
}
