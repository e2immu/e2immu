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

package org.e2immu.analyser.resolver.testexample;


import java.util.LinkedList;
import java.util.List;

public class InspectionGaps_4 {

    static void method0(Integer i) {
        Number n = i;
    }
    interface IntegerList extends List<Integer> {
    }

    static void method1(List<Number> numbers) {
        List<?> list1 = numbers;
        List<Number> list2 = numbers;
        List<? extends Number> list3 = numbers;
        // FAILS List<? extends Integer> list = numbers;
        // FAILS List<Integer> list = numbers;
        // FAILS IntegerList list = numbers;
    }

    static void method1(LinkedList<Number> numbers) {
        List<?> list1 = numbers;
        List<Number> list2 = numbers;
        List<? extends Number> list3 = numbers;
        // FAILS List<? extends Integer> list = numbers;
        // FAILS List<Integer> list = numbers;
        // FALSE IntegerList list = numbers;
    }


    static void method2(LinkedList<Integer> integers) {
        List<?> list1 = integers;
        // FAILS List<Number> list2 = integers;
        // FAILS LinkedList<Number> numbers = integers;
        List<? extends Number> list3 = integers;
        LinkedList<? extends Number> list4 = integers;
        List<? extends Integer> list5 = integers;
        LinkedList<? extends Integer> list6 = integers;
        List<Integer> list7 = integers;
        // FAILS IntegerList list8 = integers;
    }

    static void method2(IntegerList integers) {
        List<?> list1 = integers;
        // FAILS List<Number> list2 = integers;
        // FAILS LinkedList<Number> numbers = integers;
        List<? extends Number> list3 = integers;
        // FAILS LinkedList<? extends Number> list4 = integers;
        List<? extends Integer> list5 = integers;
        // FAILS LinkedList<? extends Integer> list6 = integers;
        List<Integer> list7 = integers;
    }

    static void method3(List<Integer> integers) {
        List<?> list1 = integers;
        // FAILS List<Number> list2 = integers;
        List<? extends Integer> list3 = integers;
        // FAILS LinkedList<Integer> list4 = integers;
        // FAILS  IntegerList list = integers;
    }
}
