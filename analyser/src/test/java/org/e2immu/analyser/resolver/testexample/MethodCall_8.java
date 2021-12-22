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

import java.util.List;
import java.util.Set;

/*
A bit contrived
 */
public class MethodCall_8<A, B> {

    public void method(List<A> list1, List<A> list2, List<A> list3) {
    }

    public void method(List<B> list1, Set<A> set2, List<B> list3) {
    }

    public void method(Set<A> set1, List<B> list2, List<B> list3) {
    }

    public void test(A a, B b) {
        method(List.of(a), List.of(a), List.of(a));
        //compilation error: method(List.of(b), List.of(a),  List.of(a));
        method(List.of(b), Set.of(a), List.of(b));
        method(Set.of(a), List.of(b), List.of(b));
    }
}
